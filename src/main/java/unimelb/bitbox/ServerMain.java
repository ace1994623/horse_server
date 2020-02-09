package unimelb.bitbox;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.util.Base64;
import java.util.logging.Logger;

import unimelb.bitbox.util.*;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;
import weka.classifiers.Classifier;
import weka.core.Instances;
import weka.core.SerializationHelper;
import weka.core.converters.CSVLoader;

import javax.net.ServerSocketFactory;

public class ServerMain implements FileSystemObserver {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	protected static FileSystemManager fileSystemManager;
	private static String videoName;
	static final String HORSE_DIRECTORY = Configuration.getConfigurationValue("horsePath");
	static final File SHARE_DIRECTORY = new File("/home/ubuntu/Desktop/horse/share/");
	private static boolean nextStep = false;
	private static String analyzeResult;
	
	public ServerMain() throws NumberFormatException, IOException, NoSuchAlgorithmException {
		fileSystemManager=new FileSystemManager(Configuration.getConfigurationValue("path"),this);
	}

	@Override
	public void processFileSystemEvent(FileSystemEvent fileSystemEvent) {
		log.info("new file create" + fileSystemEvent.pathName);
		switch (fileSystemEvent.event){
			case FILE_CREATE:
				if(fileSystemEvent.pathName.contains("cleaned_")){
					useWeka(fileSystemEvent.pathName);
				}
				else if(fileSystemEvent.pathName.contains("_1030000.csv"))
				{
					cleanCSV(fileSystemEvent.pathName);
				}
				break;
		}
	}

	/**
	 * To create a Server to keep waiting for peers's connection
	 *
	 * @param port the local port of the server itself
	 */
	static void createServerSocket(int port) {
		ServerSocketFactory factory = ServerSocketFactory.getDefault();
		try (ServerSocket server = factory.createServerSocket(port)) {
			log.info("Waiting for client connection.");

			// Wait for connections.
			while (true) {
				Socket client = server.accept();
				BufferedReader bufferIn = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
				BufferedWriter bufferOut = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8));
				log.info("One client is applying for connection.");
				// receive a video from client
				receiveVideo(client, bufferIn, bufferOut);
				
				try {
					Thread.sleep(3000);

				} catch (InterruptedException e) {
					log.info(e.getMessage());
					Thread.currentThread().interrupt();
				}

				useDeepLabCut();

				while(!nextStep){
					try {
						Thread.sleep(1000);
						Communication.inputRead(bufferIn);
						log.info("receive heart");
						bufferOut.write(Protocols.protocolResult(analyzeResult, false).toJson() + "\n");
						bufferOut.flush();
					} catch (InterruptedException e) {
						log.info(e.getMessage());
						Thread.currentThread().interrupt();
					}
				}
				// send the result to client
				bufferOut.write(Protocols.protocolResult(analyzeResult, true).toJson() + "\n");
				bufferOut.flush();
				// close the socket
				client.close();
				cleanAllFile(SHARE_DIRECTORY);
				nextStep = false;
			}
		} catch (IOException e) {
			log.warning(e.getMessage());
		}
	}

	/**
	 * use .py to cleandata the video
	 */
	static void cleanCSV(String fileName){
		log.info("cleaning the " + fileName);
		try {
			//Runtime.getRuntime().exec();
			Runtime.getRuntime().exec("python3 " + HORSE_DIRECTORY + "simple_clean.py " + HORSE_DIRECTORY + "share/" + fileName);

		}
		catch (java.io.IOException e){
			System.err.println ("IOException " + e.getMessage());
		}
	}

	/**
	 * use Weka to analyze the video
	 */
	static void useWeka(String fileName){
		log.info("use weka to analyze " + fileName );
		try{
			// 读取未标记数据集
			File file = new File(HORSE_DIRECTORY + "share/" + fileName);
			CSVLoader loader = new CSVLoader();
			loader.setFile(file);

			Instances unlabeledData = loader.getDataSet();
			unlabeledData.setClassIndex(unlabeledData.numAttributes() - 1);


			// 读取训练好的model
			Classifier trainedModel = (Classifier) SerializationHelper.read(HORSE_DIRECTORY + "modelv1.0.model");

			// 遍历每条记录
			for(int numOfIndex =0; numOfIndex < unlabeledData.numInstances(); numOfIndex++ ){
				// classify the unlabeled data
				double[] percentage = trainedModel.distributionForInstance(unlabeledData.instance(numOfIndex));
				double max = 0;
				int index = 0;
				// 保留两位小数
				DecimalFormat df = new DecimalFormat("0.00");

				for(int i = 0; i < percentage.length; i ++){
					// 判断哪种可能性大，根据model中的几种label
					log.info("the possibility of index " + i + " is: " + percentage[i]);
					if(percentage[i] > max){
						max = percentage[i];
						index = i;
					}
				}
				// 根据不同的label输出不同的话
				switch (index){
					case 0:
						analyzeResult = "It is lame, the accuracy is: " + df.format(percentage[index]);
						break;
					case 1:
						analyzeResult = "It is sound, the accuracy is: " + df.format(percentage[index]);
						break;
				}
				nextStep = true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * use Deeplabcut to analyze the video
	 */
	static void useDeepLabCut(){
		log.info("using deeplabcut to analyze video");
		try {
			Runtime.getRuntime().exec(HORSE_DIRECTORY + "use_dlc");
		}
		catch (java.io.IOException e){
			System.err.println ("IOException " + e.getMessage());
		}
	}

	/**
	 * clean all the file in share directory
	 */
	static void cleanAllFile(File share){
		log.warning("deleting all files");
		for (File file : share.listFiles()){
			if (file.isFile()) {
				log.warning("deleting : " + file.getName());
				file.delete();
			} else if (file.isDirectory()) {
				cleanAllFile(file);
			}
		}
	}

	/**
	 * receive the video from cliendt
	 * @param client
	 * @param bufferIn
	 * @param bufferOut
	 */
	static void receiveVideo(Socket client,BufferedReader bufferIn,BufferedWriter bufferOut){
		try {
			String in;
			String pathName;
			Document input;
			Document fileDescriptor;
			boolean status;
			String content;
			long fileSize;
			long blockSize = Long.parseLong(Configuration.getConfigurationValue("blockSize"));
			boolean receiveComplete = false;
			while (!receiveComplete) {
				try {
					Thread.sleep(1);
				} catch (InterruptedException e) {
					log.info(e.getMessage());
					Thread.currentThread().interrupt();
				}
				// receive data
				in = Communication.inputRead(bufferIn);

				input = Document.parse(in);
				String command = input.getString("command");
				switch (command) {
					case "FILE_CREATE_REQUEST":
						pathName = input.getString("pathName");
						videoName = pathName;
						log.info( "require to create file:\t" + pathName);
						if (fileSystemManager.isSafePathName(pathName)) {
							fileDescriptor = (Document) input.get("fileDescriptor");
							//check the existing of file firstly.
							try {
								if (!fileSystemManager.fileNameExists(pathName)) {

									status = fileSystemManager.createFileLoader(
											pathName,
											fileDescriptor.getString("md5"),
											fileDescriptor.getLong("fileSize"),
											fileDescriptor.getLong("lastModified")
									);
									if (status) {
										if (fileSystemManager.checkShortcut(input.getString("pathName"))) {

											bufferOut.write(Protocols.protocolFileDirResponse(input,
													"FILE_CREATE_RESPONSE",
													"shortcut was used",
													false).toJson() + "\n");
											bufferOut.flush();
											log.info("shortcut was used:\t" + pathName);
										} else {
											bufferOut.write(Protocols.protocolFileDirResponse(input,
													"FILE_CREATE_RESPONSE",
													"file loader ready",
													true).toJson() + "\n");
											bufferOut.flush();
											log.info("file loader ready:\t" + pathName);
											fileSize = fileDescriptor.getLong("fileSize");
											if (fileSize <= blockSize) {
												bufferOut.write(Protocols.protocolBytesRequest(input,
														0, fileSize).toJson() + "\n");
												bufferOut.flush();
											} else {
												bufferOut.write(Protocols.protocolBytesRequest(input,
														0, blockSize).toJson() + "\n");
												bufferOut.flush();
											}
											log.info(pathName + "\tBytesRequest protocol has been send!\t");
										}
									}
								}
							} catch (IOException e) {
								log.warning(e.getMessage());
								log.warning("there was a problem creating the file:\t" + pathName);
							}
						} else {
							log.warning("unsafe pathname given:\t" + pathName);
						}
						break;
					case "FILE_BYTES_RESPONSE":
						fileDescriptor = (Document) input.get("fileDescriptor");
						long position = input.getLong("position");
						content = input.getString("content");
						pathName = input.getString("pathName");
						long length = input.getLong("length");
						fileSize = fileDescriptor.getLong("fileSize");
						status = input.getBoolean("status");
						try {
							if (status) {
								ByteBuffer fileBuffer = Base64.getDecoder().decode(ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8)));
								fileSystemManager.writeFile(pathName, fileBuffer, position);
								if (position + length == fileSize){
									fileSystemManager.checkWriteComplete(pathName);
									log.info("Receive " + pathName + " successful.");
									fileSystemManager.cancelFileLoader(pathName);
									receiveComplete = true;
								}else{
									if (fileSize - (position + length) <= blockSize) {
										bufferOut.write(Protocols.protocolBytesRequest(input,
												position + length, fileSize - (position + length)).toJson() + "\n");
									} else {
										bufferOut.write(Protocols.protocolBytesRequest(input,
												position + length, blockSize).toJson() + "\n");
									}
									bufferOut.flush();
								}
								log.info(input.getString("message"));
								log.info(pathName + " is loading :\t" + String.format("%.2f", ((double) (position + length) / (double) fileSize) * 100) + " %");
							} else {
								log.warning("1 failed to write byte to file:\t" + pathName);
								fileSystemManager.cancelFileLoader(pathName);
							}
						} catch (Exception e) {
							e.printStackTrace();
							log.warning("2 failed to write byte to file:\t" + pathName);
							fileSystemManager.cancelFileLoader(pathName);
						}
						break;
				}
			}
		} catch (Exception e) {
			log.warning(e.getMessage());
			log.info("connection closed\t");
			try {
				client.close();
			} catch (Exception a) {
				log.warning(a.getMessage());
			}
		}
	}
}
