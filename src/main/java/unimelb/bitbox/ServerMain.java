//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.logging.Logger;
import javax.net.ServerSocketFactory;
import unimelb.bitbox.util.Communication;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemObserver;
import unimelb.bitbox.util.Protocols;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;
import weka.classifiers.Classifier;
import weka.core.Instances;
import weka.core.SerializationHelper;
import weka.core.converters.CSVLoader;

public class ServerMain implements FileSystemObserver {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	protected static FileSystemManager fileSystemManager;
	private static ArrayList<String> videoName = new ArrayList();
	static final String HORSE_DIRECTORY = Configuration.getConfigurationValue("horsePath");
	static final File SHARE_DIRECTORY = new File("/home/ubuntu/Desktop/horse/share/");
	static final int NUMBER_OF_VIDEOS = 3;
	private static boolean nextStep = false;
	private static boolean isFirstFinished = false;
	private static StringBuffer analyzeResult = new StringBuffer();

	public ServerMain() throws NumberFormatException, IOException, NoSuchAlgorithmException {
		fileSystemManager = new FileSystemManager(Configuration.getConfigurationValue("path"), this);
	}

	public void processFileSystemEvent(FileSystemEvent fileSystemEvent) {
		switch(fileSystemEvent.event) {
			case FILE_CREATE:
				log.info("new file create" + fileSystemEvent.pathName);
				if (fileSystemEvent.pathName.contains("cleaned_")) {
					useWeka(fileSystemEvent.pathName);
				} else if (fileSystemEvent.pathName.contains("_1030000.csv") || fileSystemEvent.pathName.contains("_950000.csv")) {
					cleanCSV(fileSystemEvent.pathName);
				}
			default:
		}
	}

	static void createServerSocket(int port) {
		ServerSocketFactory factory = ServerSocketFactory.getDefault();

		try {
			ServerSocket server = factory.createServerSocket(port);
			Throwable var3 = null;

			try {
				log.info("Waiting for client connection.");

				while(true) {
					Socket client = server.accept();
					nextStep = false;
					isFirstFinished = false;
					videoName = new ArrayList();
					analyzeResult = new StringBuffer();
					BufferedReader bufferIn = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
					BufferedWriter bufferOut = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8));
					log.info("One client is applying for connection.");

					for(int i = 0; i < 3; ++i) {
						receiveVideo(client, bufferIn, bufferOut);
					}

					try {
						Thread.sleep(3000L);
					} catch (InterruptedException var19) {
						log.info(var19.getMessage());
						Thread.currentThread().interrupt();
					}

					useDeepLabCut();
					boolean count = false;

					while(!nextStep) {
						try {
							Thread.sleep(1000L);
							if (isFirstFinished && !count) {
								count = true;
								Thread analyzeVideo = new Thread(ServerMain::useDeepLabCut);
								analyzeVideo.start();
							}

							Communication.inputRead(bufferIn);
							log.info("receive heart");
							bufferOut.write(Protocols.protocolResult("no result", false).toJson() + "\n");
							bufferOut.flush();
						} catch (InterruptedException var18) {
							log.info(var18.getMessage());
							Thread.currentThread().interrupt();
						}
					}

					bufferOut.write(Protocols.protocolResult(analyzeResult.toString(), true).toJson() + "\n");
					bufferOut.flush();
					client.close();
					cleanAllFile(SHARE_DIRECTORY);
					nextStep = false;
				}
			} catch (Throwable var20) {
				var3 = var20;
				throw var20;
			} finally {
				if (server != null) {
					if (var3 != null) {
						try {
							server.close();
						} catch (Throwable var17) {
							var3.addSuppressed(var17);
						}
					} else {
						server.close();
					}
				}

			}
		} catch (IOException var22) {
			log.warning(var22.getMessage());
		}
	}

	static void cleanCSV(String fileName) {
		log.info("cleaning the " + fileName);

		try {
			if (getOriginName(fileName).equals(getPureName((String)videoName.get(0)))) {
				Runtime.getRuntime().exec("python3 " + HORSE_DIRECTORY + "simple_clean.py " + HORSE_DIRECTORY + "share/" + fileName);
			} else if (getOriginName(fileName).equals(getPureName((String)videoName.get(1)))) {
				Runtime.getRuntime().exec("python3 " + HORSE_DIRECTORY + "front_clean.py " + HORSE_DIRECTORY + "share/" + fileName);
			} else {
				Runtime.getRuntime().exec("python3 " + HORSE_DIRECTORY + "back_clean.py " + HORSE_DIRECTORY + "share/" + fileName);
			}
		} catch (IOException var2) {
			System.err.println("IOException " + var2.getMessage());
		}

	}

	static String getOriginName(String oldName) {
		int postIndex = oldName.indexOf("DLC");
		String newName = oldName.substring(0, postIndex);
		log.info("$$$$$$$$$$$$$$$$$the name is " + newName);
		return newName;
	}

	static void useWeka(String fileName) {
		log.info("use weka to analyze " + fileName);

		try {
			File file = new File(HORSE_DIRECTORY + "share/" + fileName);
			CSVLoader loader = new CSVLoader();
			loader.setFile(file);
			Instances unlabeledData = loader.getDataSet();
			unlabeledData.setClassIndex(unlabeledData.numAttributes() - 1);
			Classifier trainedModel;
			byte numOfVideo;
			if (getOriginName(fileName).equals("cleaned_" + getPureName((String)videoName.get(0)))) {
				trainedModel = (Classifier)SerializationHelper.read(HORSE_DIRECTORY + "model_NaiveBayes_1.model");
				numOfVideo = 1;
			} else if (getOriginName(fileName).equals("cleaned_" + getPureName((String)videoName.get(1)))) {
				trainedModel = (Classifier)SerializationHelper.read(HORSE_DIRECTORY + "front_model_NaiveBayes.model");
				numOfVideo = 2;
			} else {
				trainedModel = (Classifier)SerializationHelper.read(HORSE_DIRECTORY + "butt_model_NaiveBayes.model");
				numOfVideo = 3;
			}

			log.info("@@@@@@@@@@@@@@@@@@@@@@@@@@now the video is " + numOfVideo);

			for(int numOfIndex = 0; numOfIndex < unlabeledData.numInstances(); ++numOfIndex) {
				double[] percentage = trainedModel.distributionForInstance(unlabeledData.instance(numOfIndex));
				double max = 0.0D;
				int index = 0;
				DecimalFormat df = new DecimalFormat("0.00");

				for(int i = 0; i < percentage.length; ++i) {
					log.info("the possibility of index " + i + " is: " + percentage[i] + " (video " + numOfVideo + ")");
					if (percentage[i] > max) {
						max = percentage[i];
						index = i;
					}
				}

				log.info("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!now the index is " + index);
				String currentResult;
				if (numOfVideo == 1) {
					switch(index) {
						case 0:
							currentResult = "It is lame, the accuracy is: " + df.format(percentage[index]) + System.lineSeparator();
							analyzeResult.append(currentResult);
							isFirstFinished = true;
							break;
						case 1:
							currentResult = "It is sound, the accuracy is: " + df.format(percentage[index]) + System.lineSeparator();
							analyzeResult.append(currentResult);
							nextStep = true;
					}
				} else if (numOfVideo == 2) {
					switch(index) {
						case 0:
							currentResult = "The left forelimb is lame, the accuracy is: " + df.format(percentage[index]) + System.lineSeparator();
							analyzeResult.append(currentResult);
							currentResult = "The right forelimb is sound, the accuracy is: " + df.format(1.0D - percentage[1]) + System.lineSeparator();
							analyzeResult.append(currentResult);
							break;
						case 1:
							currentResult = "The right forelimb is lame, the accuracy is: " + df.format(percentage[index]) + System.lineSeparator();
							analyzeResult.append(currentResult);
							currentResult = "The left forelimb is sound, the accuracy is: " + df.format(1.0D - percentage[0]) + System.lineSeparator();
							analyzeResult.append(currentResult);
							break;
						case 2:
							currentResult = "Both forelimbs are sound, the accuracy is: " + df.format(percentage[index]) + System.lineSeparator();
							analyzeResult.append(currentResult);
					}
				} else {
					switch(index) {
						case 0:
							currentResult = "The left hind limb is lame, the accuracy is: " + df.format(percentage[index]) + System.lineSeparator();
							analyzeResult.append(currentResult);
							currentResult = "The right hind limb is sound, the accuracy is: " + df.format(1.0D - percentage[1]) + System.lineSeparator();
							analyzeResult.append(currentResult);
							break;
						case 1:
							currentResult = "The right hind limb is lame, the accuracy is: " + df.format(percentage[index]) + System.lineSeparator();
							analyzeResult.append(currentResult);
							currentResult = "The left hind limb is sound, the accuracy is: " + df.format(1.0D - percentage[0]) + System.lineSeparator();
							analyzeResult.append(currentResult);
							break;
						case 2:
							currentResult = "Both hind limbs are sound, the accuracy is: " + df.format(percentage[index]) + System.lineSeparator();
							analyzeResult.append(currentResult);
					}

					nextStep = true;
				}
			}
		} catch (Exception var14) {
			var14.printStackTrace();
		}

	}

	private static String getPureName(String oldName) {
		int index = oldName.lastIndexOf(".");
		return oldName.substring(0, index);
	}

	static void useDeepLabCut() {
		log.info("using deeplabcut to analyze video");

		try {
			File file = new File(HORSE_DIRECTORY + "share/open_deeplabcut.py");
			BufferedWriter out;
			if (!isFirstFinished) {
				file.createNewFile();
				log.info("first .py file create successful");
				out = new BufferedWriter(new FileWriter(file));
				out.write("import deeplabcut\r\n");
				out.write("config_path = '/home/ubuntu/horse-Richard-2020-02-05/config.yaml'\r\n");
				out.write("deeplabcut.analyze_videos(config_path,['/home/ubuntu/Desktop/horse/share/" + (String)videoName.get(0) + "'], videotype='.mp4', save_as_csv=True)\n\r\n");
				out.flush();
				out.close();
			} else {
				file.delete();
				file.createNewFile();
				out = new BufferedWriter(new FileWriter(file));
				out.write("import deeplabcut\r\n");
				out.write("config_path = '/home/ubuntu/horse-front-Richard-2020-02-16/config.yaml'\r\n");
				out.write("deeplabcut.analyze_videos(config_path,['/home/ubuntu/Desktop/horse/share/" + (String)videoName.get(1) + "'], videotype='.mp4', save_as_csv=True)\n\r\n");
				out.write("config_path = '/home/ubuntu/horse-back-Richard-2020-02-16/config.yaml'\r\n");
				out.write("deeplabcut.analyze_videos(config_path,['/home/ubuntu/Desktop/horse/share/" + (String)videoName.get(2) + "'], videotype='.mp4', save_as_csv=True)\n\r\n");
				out.flush();
				out.close();
			}

			Runtime.getRuntime().exec(HORSE_DIRECTORY + "use_dlc");
		} catch (IOException var2) {
			System.err.println("IOException " + var2.getMessage());
		}

	}

	static void cleanAllFile(File share) {
		log.warning("deleting all files");
		File[] var1 = share.listFiles();
		int var2 = var1.length;

		for(int var3 = 0; var3 < var2; ++var3) {
			File file = var1[var3];
			if (file.isFile()) {
				log.warning("deleting : " + file.getName());
				file.delete();
			} else if (file.isDirectory()) {
				cleanAllFile(file);
			}
		}

	}

	static void receiveVideo(Socket client, BufferedReader bufferIn, BufferedWriter bufferOut) {
		try {
			long blockSize = Long.parseLong(Configuration.getConfigurationValue("blockSize"));
			boolean receiveComplete = false;

			while(!receiveComplete) {
				try {
					Thread.sleep(1L);
				} catch (InterruptedException var25) {
					log.info(var25.getMessage());
					Thread.currentThread().interrupt();
				}

				String in = Communication.inputRead(bufferIn);
				Document input = Document.parse(in);
				String command = input.getString("command");
				byte var16 = -1;
				switch(command.hashCode()) {
					case -2087261480:
						if (command.equals("FILE_BYTES_RESPONSE")) {
							var16 = 1;
						}
						break;
					case -595110065:
						if (command.equals("FILE_CREATE_REQUEST")) {
							var16 = 0;
						}
				}

				String pathName;
				Document fileDescriptor;
				boolean status;
				long fileSize;
				switch(var16) {
					case 0:
						pathName = input.getString("pathName");
						videoName.add(pathName);
						log.info("require to create file:\t" + pathName);
						if (fileSystemManager.isSafePathName(pathName)) {
							fileDescriptor = (Document)input.get("fileDescriptor");

							try {
								if (!fileSystemManager.fileNameExists(pathName)) {
									status = fileSystemManager.createFileLoader(pathName, fileDescriptor.getString("md5"), fileDescriptor.getLong("fileSize"), fileDescriptor.getLong("lastModified"));
									if (status) {
										if (fileSystemManager.checkShortcut(input.getString("pathName"))) {
											bufferOut.write(Protocols.protocolFileDirResponse(input, "FILE_CREATE_RESPONSE", "shortcut was used", false).toJson() + "\n");
											bufferOut.flush();
											log.info("shortcut was used:\t" + pathName);
										} else {
											bufferOut.write(Protocols.protocolFileDirResponse(input, "FILE_CREATE_RESPONSE", "file loader ready", true).toJson() + "\n");
											bufferOut.flush();
											log.info("file loader ready:\t" + pathName);
											fileSize = fileDescriptor.getLong("fileSize");
											if (fileSize <= blockSize) {
												bufferOut.write(Protocols.protocolBytesRequest(input, 0L, fileSize).toJson() + "\n");
												bufferOut.flush();
											} else {
												bufferOut.write(Protocols.protocolBytesRequest(input, 0L, blockSize).toJson() + "\n");
												bufferOut.flush();
											}

											log.info(pathName + "\tBytesRequest protocol has been send!\t");
										}
									}
								}
							} catch (IOException var24) {
								log.warning(var24.getMessage());
								log.warning("there was a problem creating the file:\t" + pathName);
							}
						} else {
							log.warning("unsafe pathname given:\t" + pathName);
						}
						break;
					case 1:
						fileDescriptor = (Document)input.get("fileDescriptor");
						long position = input.getLong("position");
						String content = input.getString("content");
						pathName = input.getString("pathName");
						long length = input.getLong("length");
						fileSize = fileDescriptor.getLong("fileSize");
						status = input.getBoolean("status");

						try {
							if (status) {
								ByteBuffer fileBuffer = Base64.getDecoder().decode(ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8)));
								fileSystemManager.writeFile(pathName, fileBuffer, position);
								if (position + length == fileSize) {
									fileSystemManager.checkWriteComplete(pathName);
									log.info("Receive " + pathName + " successful.");
									fileSystemManager.cancelFileLoader(pathName);
									receiveComplete = true;
								} else {
									if (fileSize - (position + length) <= blockSize) {
										bufferOut.write(Protocols.protocolBytesRequest(input, position + length, fileSize - (position + length)).toJson() + "\n");
									} else {
										bufferOut.write(Protocols.protocolBytesRequest(input, position + length, blockSize).toJson() + "\n");
									}

									bufferOut.flush();
								}

								log.info(input.getString("message"));
								log.info(pathName + " is loading :\t" + String.format("%.2f", (double)(position + length) / (double)fileSize * 100.0D) + " %");
							} else {
								log.warning("1 failed to write byte to file:\t" + pathName);
								fileSystemManager.cancelFileLoader(pathName);
							}
						} catch (Exception var23) {
							var23.printStackTrace();
							log.warning("2 failed to write byte to file:\t" + pathName);
							fileSystemManager.cancelFileLoader(pathName);
						}
				}
			}
		} catch (Exception var26) {
			log.warning(var26.getMessage());
			log.info("connection closed\t");

			try {
				client.close();
			} catch (Exception var22) {
				log.warning(var22.getMessage());
			}
		}

	}
}
