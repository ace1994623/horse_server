package unimelb.bitbox.util;

import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

/**
 * all protocol generation methods
 *
 *
 */

public class Protocols {

	/**
	 * To generate a RESPONSE json document of file and directory protocol
	 *
	 * @param doc      REQUEST document
	 * @param protocol such like "FILE_CREATE_RESPONSE"
	 * @param message  debug message
	 * @param status   boolean
	 * @return Document
	 */
	public static Document protocolFileDirResponse(Document doc, String protocol, String message, boolean status) {
		Document newDoc = new Document();
		newDoc.append("command", protocol);
		switch (protocol) {
			case "FILE_CREATE_RESPONSE":
			case "FILE_DELETE_RESPONSE":
			case "FILE_MODIFY_RESPONSE":
				Document sub_doc = (Document) doc.get("fileDescriptor");
				newDoc.append("fileDescriptor", sub_doc);
				break;
		}
		newDoc.append("pathName", doc.getString("pathName"));
		newDoc.append("message", message);
		newDoc.append("status", status);
		return newDoc;
	}

	/**
	 * To generate a json document of result
	 *
	 * @return json document
	 */
	public static Document protocolResult(String result, boolean status) {
		Document doc = new Document();
		doc.append("command", "RESULT");
		doc.append("message", result);
		doc.append("status", status);
		return doc;
	}

	/**
	 * To generate a REQUEST json document of FILE_BYTES_REQUEST
	 *
	 * @param doc      file document
	 * @param position file position
	 * @param length   file length
	 * @return Document
	 */
	public static Document protocolBytesRequest(Document doc, long position, long length) {
		Document newDoc = new Document();
		newDoc.append("command", "FILE_BYTES_REQUEST");
		Document sub_doc = (Document) doc.get("fileDescriptor");
		newDoc.append("fileDescriptor", sub_doc);
		newDoc.append("pathName", doc.getString("pathName"));
		newDoc.append("position", position);
		newDoc.append("length", length);
		return newDoc;
	}

	/**
	 * To generate a RESPONSE json document of FILE_BYTES_RESPONSE
	 *
	 * @param doc     file document from FILE_BYTES_REQUEST
	 * @param content bytes encoded in Base64 format
	 * @param message debug message
	 * @param status  boolean
	 * @return Document
	 */
	public static Document protocolBytesResponse(Document doc, String content, String message, boolean status) {
		Document newDoc = new Document();
		newDoc.append("command", "FILE_BYTES_RESPONSE");
		Document sub_doc = (Document) doc.get("fileDescriptor");
		newDoc.append("fileDescriptor", sub_doc);
		newDoc.append("pathName", doc.getString("pathName"));
		newDoc.append("position", doc.getLong("position"));
		long length = doc.getLong("length");
		if (Configuration.getConfigurationValue("mode").toLowerCase().equals("udp") && length > 8100)
			length = 8100;
		newDoc.append("length", length);
		newDoc.append("content", content);
		newDoc.append("message", message);
		newDoc.append("status", status);
		return newDoc;
	}

	public static Document protocolConnect(HostPort peer, Boolean status, String message) {
		Document newDoc = new Document();
		newDoc.append("command", "CONNECT_PEER_RESPONSE");
		newDoc.append("host", peer.host);
		newDoc.append("port", peer.port);
		newDoc.append("status", status);
		newDoc.append("message", message);
		return newDoc;
	}

	public static Document protocolDisconnect(HostPort peer, Boolean status, String message) {
		Document newDoc = new Document();
		newDoc.append("command", "DISCONNECT_PEER_RESPONSE");
		newDoc.append("host", peer.host);
		newDoc.append("port", peer.port);
		newDoc.append("status", status);
		newDoc.append("message", message);
		return newDoc;
	}

	public static Document protocolAuthRes(String Aes128, Boolean status, String message) {
		Document newDoc = new Document();
		newDoc.append("command", "AUTH_RESPONSE");
		if (status)
			newDoc.append("AES128", Aes128);
		newDoc.append("status", status);
		newDoc.append("message", message);
		return newDoc;
	}

}
