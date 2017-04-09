package de.intranda.goobi.plugins.dump;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.faces.context.FacesContext;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang.StringUtils;

import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.FacesContextHelper;
import de.sub.goobi.helper.NIOFileUtils;
import lombok.Data;
import lombok.extern.log4j.Log4j;

@Log4j
@Data
public class Exporter {

	private int numberAllFiles = 0;
	private int numberCurrentFile = 0;
	private List<Message> messageList;
	private boolean confirmation = false;

	private String command;
	private String SQL_DUMP_PATH = "/opt/digiverso/goobi/tmp/goobi.sql";
	private String ZIP_SQL_DUMP_PATH = "/sql";

	public Exporter(XMLConfiguration config) {
		confirmation = false;
		messageList = new ArrayList<Message>();
		command = config.getString("commandExport", "/bin/sh/export.sh");
	}

	/**
	 * start the export of the entire selected content into a zip file for download
	 */
	public void startExport() {
		try {
			// create an SQL dump
			messageList.add(new Message("Creating database dump.", MessageStatus.OK));
			String myCommand = command.replaceAll("DATABASE_TEMPFILE", SQL_DUMP_PATH);
			String[] commandArray = myCommand.split(", ");

			Process runtimeProcess = Runtime.getRuntime().exec(commandArray);
			int processComplete = runtimeProcess.waitFor();

			// check if SQL dump generation was successfull
			if (processComplete == 0) {
				messageList.add(new Message("Created SQL dump successfully", MessageStatus.OK));
			} else {
				messageList.add(new Message("Error during creation of database dump", MessageStatus.ERROR));
				return;
			}

			// prepare zip generation
			FacesContext facesContext = FacesContextHelper.getCurrentFacesContext();
			HttpServletResponse response = (HttpServletResponse) facesContext.getExternalContext().getResponse();
			OutputStream out = response.getOutputStream();
			response.setContentType("application/zip");
			response.setHeader("Content-Disposition", "attachment;filename=\"goobidump.zip\"");
			ZipOutputStream zos = new ZipOutputStream(out);

			// add database into zip
			messageList.add(new Message("Add database dump to archive", MessageStatus.OK));
			Path sqlDump = Paths.get(SQL_DUMP_PATH);
			addDirToArchive(zos, sqlDump, ZIP_SQL_DUMP_PATH);

			// add all rulesets into zip
			String rulesetfolder = ConfigurationHelper.getInstance().getRulesetFolder();
			messageList.add(new Message("Add ruleset folder to archive", MessageStatus.OK));
			numberAllFiles = DumpHelper.getFilesCount(new File(rulesetfolder));
			numberCurrentFile = 0;
			Path srcDir = Paths.get(rulesetfolder);
			addDirToArchive(zos, srcDir, "/opt/digiverso/goobi");
			
			// add all configurations into zip
			String configfolder = ConfigurationHelper.getInstance().getConfigurationFolder();
			messageList.add(new Message("Add configuration folder to archive", MessageStatus.OK));
			numberAllFiles = DumpHelper.getFilesCount(new File(configfolder));
			numberCurrentFile = 0;
			srcDir = Paths.get(configfolder);
			addDirToArchive(zos, srcDir, "/opt/digiverso/goobi");
			
			// add all scripts into zip
			String scriptfolder = ConfigurationHelper.getInstance().getScriptsFolder();
			messageList.add(new Message("Add scripts folder to archive", MessageStatus.OK));
			numberAllFiles = DumpHelper.getFilesCount(new File(scriptfolder));
			numberCurrentFile = 0;
			srcDir = Paths.get(scriptfolder);
			addDirToArchive(zos, srcDir, "/opt/digiverso/goobi");
			
			// add all dockets into zip
			String xsltfolder = ConfigurationHelper.getInstance().getXsltFolder();
			messageList.add(new Message("Add dockets folder to archive", MessageStatus.OK));
			numberAllFiles = DumpHelper.getFilesCount(new File(xsltfolder));
			numberCurrentFile = 0;
			srcDir = Paths.get(rulesetfolder);
			addDirToArchive(zos, srcDir, "/opt/digiverso/goobi");
						
			// add all metadata content into zip
			String metafolder = ConfigurationHelper.getInstance().getMetadataFolder();
			messageList.add(new Message("Add metadata folder to archive", MessageStatus.OK));
			numberAllFiles = DumpHelper.getFilesCount(new File(metafolder));
			numberCurrentFile = 0;
			srcDir = Paths.get(metafolder);
			addDirToArchive(zos, srcDir, "/opt/digiverso/goobi");

			// close all connections and streams
			zos.close();
			out.flush();
			facesContext.responseComplete();
		} catch (IOException | InterruptedException e) {
			log.error("Exception while executing the download preparation", e);
			messageList.add(new Message("Exception while executing the download preparation: " + e.getMessage(),
					MessageStatus.ERROR));
		}
	}

	/**
	 * private method to add content to zip file
	 * 
	 * @param zos
	 * @param srcFile
	 * @param parrentDirectoryName
	 */
	private void addDirToArchive(ZipOutputStream zos, Path srcFile, String parrentDirectoryName) throws IOException {
		String zipEntryName = srcFile.toFile().getName();
		if (StringUtils.isNotBlank(parrentDirectoryName)) {
			zipEntryName = parrentDirectoryName + "/" + srcFile.toFile().getName();
		}

		if (Files.isDirectory(srcFile)) {
			messageList.add(new Message("Add folder " + zipEntryName + " to archive.", MessageStatus.OK));
			for (Path file : NIOFileUtils.listFiles(srcFile.toString())) {
				addDirToArchive(zos, file, zipEntryName);
				continue;
			}

		} else {
			numberCurrentFile++;
			// create byte buffer
			byte[] buffer = new byte[1024];
			FileInputStream fis = new FileInputStream(srcFile.toFile());
			zos.putNextEntry(new ZipEntry(zipEntryName));
			int length;
			while ((length = fis.read(buffer)) > 0) {
				zos.write(buffer, 0, length);
			}
			zos.closeEntry();

			// close the InputStream
			fis.close();
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
