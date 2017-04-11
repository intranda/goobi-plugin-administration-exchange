package de.intranda.goobi.plugins.dump;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.io.FileUtils;
import org.primefaces.event.FileUploadEvent;

import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.NIOFileUtils;
import lombok.Data;
import lombok.extern.log4j.Log4j;

@Log4j
@Data
public class Importer {
	
	private int numberAllFiles = 0;
	private int numberCurrentFile = 0;
	private List<Message> messageList;
	private Path importFile;

	private boolean confirmation = false;
	private boolean includeRulesets = false;
	private boolean includeScripts = false;
	private boolean includeConfiguration = false;
	private boolean includeMetadata = false;
	private boolean includeDockets = false;
	private boolean includePlugins = false;
	private boolean includeSQLdump = false;
	
	private String command;
	private String TMP_FOLDER = "/opt/digiverso/goobi/tmp/";

	public Importer(XMLConfiguration config) {
		messageList = new ArrayList<Message>();
		command = config.getString("commandImport", "/bin/sh/import.sh");
	}
	
	/**
	 * public Eventhandler to allow a file upload
	 * 
	 * @param event
	 */
	public void uploadFile(FileUploadEvent event) {
		numberAllFiles = 0;
		numberCurrentFile = 0;
		messageList = new ArrayList<Message>();
		
		// upload the file and store it in the filesystem
		try {
			String filename = event.getFile().getFileName();
			storeUploadedFile(filename, event.getFile().getInputstream());
		} catch (IOException e) {
			log.error("IOException while uploading the zip file", e);
			messageList.add(
					new Message("IOException while uploading the zip file: " + e.getMessage(), MessageStatus.ERROR));
		}
		
		// start the unzipping
		try {
			unzipUploadedFile();
		} catch (IOException e) {
			log.error("IOException while unzipping the uploaded file", e);
			messageList.add(
					new Message("IOException while unzipping the uploaded: " + e.getMessage(), MessageStatus.ERROR));
		}
		
		// start to replace the goobi content with the content of the uploaded unzipped file 
//		replaceContent();
	}
	
	/**
	 * internal method to store the file that was uploaded
	 * 
	 * @param fileName
	 * @param in
	 */
	private void storeUploadedFile(String fileName, InputStream in) {
		OutputStream out = null;
		try {
			String extension = fileName.substring(fileName.indexOf("."));
			importFile = Files.createTempFile(fileName, extension);
			out = new FileOutputStream(importFile.toFile());

			int read = 0;
			byte[] bytes = new byte[1024];
			while ((read = in.read(bytes)) != -1) {
				out.write(bytes, 0, read);
			}
			out.flush();
		} catch (IOException e) {
			log.error("IOException while copying the file " + fileName, e);
			messageList.add(new Message("IOException while copying the file: " + e.getMessage(), MessageStatus.ERROR));
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					log.error("Error while closing the InputStream", e);
					messageList.add(
							new Message("Error while closing the InputStream: " + e.getMessage(), MessageStatus.ERROR));
				}
			}
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
					log.error("Error while closing the OutputStream", e);
					messageList.add(new Message("Error while closing the OutputStream: " + e.getMessage(),
							MessageStatus.ERROR));
				}
			}
		}
	}

	/**
	 * internal method for unzipping the content of an uploaded zip file
	 */
	private void unzipUploadedFile() throws IOException{
		messageList.add(new Message("Starting to extract the uploaded ZIP file", MessageStatus.OK));
//		ZipInputStream zis = new ZipInputStream(new FileInputStream(importFile.toFile()));
//		ZipEntry ze = zis.getNextEntry();
//		while (ze != null) {
//			String fileName = ze.getName();
//			Path newFile = Paths.get(TMP_FOLDER, fileName);
//			try{
//				Files.createDirectories(newFile.getParent());
//			}catch(FileAlreadyExistsException e){
//				log.info("Folder does exist already and does not get created again: " + newFile.getParent());
//			}
//			FileOutputStream fos = new FileOutputStream(newFile.toFile());
//			byte[] buffer = new byte[1024];
//			int len;
//			while ((len = zis.read(buffer)) > 0) {
//				fos.write(buffer, 0, len);
//			}
//			fos.close();
//			ze = zis.getNextEntry();
//		}
//		zis.closeEntry();
//		zis.close();
		
		
	
		// Open the file
		ZipFile file = new ZipFile(importFile.toFile());
		FileSystem fileSystem = FileSystems.getDefault();
		Enumeration<? extends ZipEntry> entries = file.entries();
		numberAllFiles = file.size();
		numberCurrentFile = 0;
		
		// Iterate over entries
		while (entries.hasMoreElements()) {
			numberCurrentFile++;
			ZipEntry entry = entries.nextElement();
			// If directory then create a new directory in uncompressed folder
			if (entry.isDirectory()) {
				messageList.add(new Message("Creating Directory:" + TMP_FOLDER + entry.getName(), MessageStatus.OK));
				Files.createDirectories(fileSystem.getPath(TMP_FOLDER + entry.getName()));
			}
			// Else create the file
			else {
				InputStream is = file.getInputStream(entry);
				BufferedInputStream bis = new BufferedInputStream(is);
				String uncompressedFileName = TMP_FOLDER + entry.getName();
				Path uncompressedFilePath = fileSystem.getPath(uncompressedFileName);
				try{
					Files.createFile(uncompressedFilePath);
				}catch (FileAlreadyExistsException e){
					messageList.add(new Message("File exists already and does not get created again:" + entry.getName(), MessageStatus.OK));
				}
				FileOutputStream fileOutput = new FileOutputStream(uncompressedFileName);
				while (bis.available() > 0) {
					fileOutput.write(bis.read());
				}
				fileOutput.close();
				messageList.add(new Message("Written :" + entry.getName(), MessageStatus.OK));
			}
		}
		messageList.add(new Message("ZIP file successfully extracted.", MessageStatus.OK));
	}
	
	/**
	 * internal method to replace the Goobi content with new content from the unzipped file
	 */
	private void replaceContent(){
		Path sqlFolder = Paths.get(TMP_FOLDER, "sql");
		List<String> filesInSqlFolder = NIOFileUtils.list(sqlFolder.toString());
		Path database = Paths.get(sqlFolder.toString(), filesInSqlFolder.get(0));
		messageList.add(new Message("Starting to import the uploaded SQL dump.", MessageStatus.OK));
		String myCommand = command.replaceAll("DATABASE_TEMPFILE", TMP_FOLDER + "/sql/goobi.sql");
		String[] commandArray = myCommand.split(", ");
		
		try {
			Process runtimeProcess = Runtime.getRuntime().exec(commandArray);
			int processComplete = runtimeProcess.waitFor();
			if (processComplete == 0) {
				messageList.add(new Message("SQL dump successfully imported", MessageStatus.OK));
			} else {
				messageList.add(new Message("Error during creation of database dump", MessageStatus.ERROR));
				return;
			}
			messageList.add(new Message("Start replacing metadata folders.", MessageStatus.OK));
			messageList.add(new Message("Delete old metadata folder.", MessageStatus.OK));
			String METADATA_FOLDER = ConfigurationHelper.getInstance().getMetadataFolder();
			FileUtils.deleteDirectory(Paths.get(METADATA_FOLDER).toFile());
			messageList.add(new Message("Old metadata folder deleted.", MessageStatus.OK));
			messageList.add(new Message("Start to import new metadata folder.", MessageStatus.OK));
			Path tmpMetadataFolder = Paths.get(TMP_FOLDER, METADATA_FOLDER);
			Files.move(tmpMetadataFolder, Paths.get(METADATA_FOLDER));
			messageList.add(new Message("Import successfully finished", MessageStatus.OK));
		} catch (IOException | InterruptedException e) {
			log.error("Exception while importing data from zip file", e);
			messageList.add(new Message("Exception while importing data from zip file: " + e.getMessage(),
					MessageStatus.ERROR));
		}
	}
	
	 /**
     * public getter to receive the progress in percent
     * 
     * @param file
     */
    public int getProgress(){
    	if (numberAllFiles==0){
    		return 0;
    	}else{
    		int result = 100 * numberCurrentFile / numberAllFiles;
    		return result;
    	}
    }
    
}
