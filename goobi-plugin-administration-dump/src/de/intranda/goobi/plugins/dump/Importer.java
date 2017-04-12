package de.intranda.goobi.plugins.dump;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

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
	private String TMP_FOLDER = "/opt/digiverso/goobi/tmp/dump";

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
		replaceContent();
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
		messageList.add(new Message("Starting to extract the uploaded ZIP file into " + TMP_FOLDER, MessageStatus.OK));
		
		Path temp = Paths.get(TMP_FOLDER);
		if (temp.toFile().exists()){
			messageList.add(new Message("Cleanup temp folder " + TMP_FOLDER + " first.", MessageStatus.OK));
			NIOFileUtils.deleteDir(temp);
		}
		
		//count the content
		ZipFile zipfile = new ZipFile(importFile.toFile());
		numberAllFiles = zipfile.size();
		numberCurrentFile = 0;
		zipfile.close();
				
		ZipInputStream zis = new ZipInputStream(new FileInputStream(importFile.toFile()));
		ZipEntry ze = zis.getNextEntry();
		while (ze != null) {
			String fileName = ze.getName();
			Path newFile = Paths.get(TMP_FOLDER, fileName);
			
			if (ze.isDirectory()) {
				messageList.add(new Message("Creating directory:" + ze.getName(), MessageStatus.OK));
				Files.createDirectories(newFile);
			} else{
				numberCurrentFile++;
				try{
					if (!newFile.getParent().toFile().exists()){
						Files.createDirectories(newFile.getParent());
						messageList.add(new Message("Creating parent directory:" + newFile.getParent(), MessageStatus.OK));
					}
				}catch(FileAlreadyExistsException e){
					log.info("Folder does exist already and does not get created again: " + newFile.getParent());
				}
				try{
					Files.createFile(newFile);
				}catch (FileAlreadyExistsException e){
					//messageList.add(new Message("File exists already and does not get created again:" + ze.getName(), MessageStatus.OK));
				}
				
				FileOutputStream fos = new FileOutputStream(newFile.toFile());
				byte[] buffer = new byte[1024];
				int len;
				while ((len = zis.read(buffer)) > 0) {
					fos.write(buffer, 0, len);
				}
				fos.close();
				ze = zis.getNextEntry();
			}
		}
		zis.closeEntry();
		zis.close();
		messageList.add(new Message("ZIP file successfully extracted.", MessageStatus.OK));
	}
	
	/**
	 * internal method to replace the Goobi content with new content from the unzipped file
	 */
	private void replaceContent(){
		messageList.add(new Message("Starting to replace existing content in Goobi.", MessageStatus.OK));
		
		try {
			if (includeSQLdump){
				Path tmpSql = Paths.get(TMP_FOLDER, "/sql/goobi.sql");
				if (tmpSql.toFile().exists()){
					String myCommand = command.replaceAll("DATABASE_TEMPFILE", TMP_FOLDER + "/sql/goobi.sql");
					String[] commandArray = myCommand.split(", ");
					Process runtimeProcess = Runtime.getRuntime().exec(commandArray);
					int processComplete = runtimeProcess.waitFor();
					if (processComplete == 0) {
						messageList.add(new Message("SQL dump successfully imported", MessageStatus.OK));
					} else {
						messageList.add(new Message("Error during importing the database dump", MessageStatus.ERROR));
						return;
					}
				} else {
					messageList.add(new Message("An SQL dump was not contained in the zip file and gets skipped.", MessageStatus.WARNING));
				}
			}
			
			// add all rulesets into zip
			if (includeRulesets){
				replaceFolder(ConfigurationHelper.getInstance().getRulesetFolder());
			}
			// add all configurations into zip
			if (includeConfiguration){
				replaceFolder(ConfigurationHelper.getInstance().getConfigurationFolder());
			}
			// add all scripts into zip
			if (includeScripts){
				replaceFolder(ConfigurationHelper.getInstance().getScriptsFolder());
			}
			// add all dockets into zip
			if (includeDockets){
				replaceFolder(ConfigurationHelper.getInstance().getXsltFolder());
			}
			// add all plugins into zip
			if (includePlugins){
				replaceFolder(ConfigurationHelper.getInstance().getPluginFolder());
			}
			// add all metadata content into zip
			if (includeMetadata){
				replaceFolder(ConfigurationHelper.getInstance().getMetadataFolder());
			}
			
			messageList.add(new Message("Entire Goobi dump import finished successfully.", MessageStatus.OK));
		} catch (IOException | InterruptedException e) {
			log.error("Exception while importing data from zip file", e);
			messageList.add(new Message("Exception while importing data from zip file: " + e.getMessage(),
					MessageStatus.ERROR));
		}
	}
	
	private void replaceFolder(String folder) throws IOException, InterruptedException {
		Path tmpMetadataFolder = Paths.get(TMP_FOLDER, folder);
		// just do the replacement if the target exists in the unzipped file
		if (tmpMetadataFolder.toFile().exists()){
			FileUtils.deleteDirectory(Paths.get(folder).toFile());	
			messageList.add(new Message("Deleted old folder: " + folder, MessageStatus.OK));
			Files.move(tmpMetadataFolder, Paths.get(folder));
			messageList.add(new Message("Folder: " + folder + " replaced successfully", MessageStatus.OK));
		} else {
			messageList.add(new Message("Folder: " + folder + " was not contained in the zip file and gets skipped.", MessageStatus.WARNING));
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
