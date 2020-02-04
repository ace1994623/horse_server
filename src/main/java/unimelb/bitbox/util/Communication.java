package unimelb.bitbox.util;

import unimelb.bitbox.ServerMain;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Logger;

public class Communication {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	/**
	 * To send a request and get a response.
	 *
	 * @return a response from other peer
	 */
	public static String inputRead(BufferedReader in) throws IOException {
		String output = "";
		try {
			while (null == (output = in.readLine())) {

			}
		} catch (IOException | ClassCastException e) {
			log.info(e.getMessage());
			throw e;
		}
		return output;
	}
}
