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
	private boolean includeRulesets = false;
	private boolean includeScripts = false;
	private boolean includeConfiguration = false;
	private boolean includeMetadata = false;
	private boolean includeDockets = false;
	private boolean includePlugins = false;
	private boolean includeSQLdump = false;
	private boolean finished = false;
	
	private List<Exclude> excludeList;
	private boolean restrict = false;
	private String restrictIDs = "";
	
	private String command;
	private String sqlFilePath;
	private String ZIP_SQL_DUMP_PATH = "/sql";

	public Exporter(XMLConfiguration config) {
		confirmation = false;
		command = config.getString("commandExport", "");
		sqlFilePath = ConfigurationHelper.getInstance().getTemporaryFolder() + "goobi.sql"; 
		
		excludeList = new ArrayList<Exclude>();
		
		int excludes = config.getMaxIndex("exclude");
        for (int i = 0; i <= excludes; i++) {
            String label = config.getString("exclude(" + i + ")[@label]");
            String regex = config.getString("exclude(" + i + ")[@regex]");
            excludeList.add(new Exclude(label, regex, false));
        }
	}

	/**
	 * start the export of the entire selected content into a zip file for download
	 */
	public void startExport() {
		finished = false;
		messageList = new ArrayList<Message>();
		try {
			// create an SQL dump
			messageList.add(new Message("Creating Goobi dump.", MessageStatus.OK));
			
			// prepare zip generation
			FacesContext facesContext = FacesContextHelper.getCurrentFacesContext();
			HttpServletResponse response = (HttpServletResponse) facesContext.getExternalContext().getResponse();
			OutputStream out = response.getOutputStream();
			response.setContentType("application/zip");
			response.setHeader("Content-Disposition", "attachment;filename=\"goobidump.zip\"");
			ZipOutputStream zos = new ZipOutputStream(out);

			// add database into zip
			if (includeSQLdump){
				if (new File (ConfigurationHelper.getInstance().getGoobiFolder() + "db/").exists()){
					addFolder(zos, ConfigurationHelper.getInstance().getGoobiFolder() + "db/", false);
				}
				
				if (command.length()>0){
					String myCommand = command.replaceAll("DATABASE_TEMPFILE", sqlFilePath);
					String[] commandArray = myCommand.split(", ");
	
					Process runtimeProcess = Runtime.getRuntime().exec(commandArray);
					int processComplete = runtimeProcess.waitFor();
	
					// check if SQL dump generation was successfull
					if (processComplete == 0) {
						messageList.add(new Message("Created SQL dump successfully.", MessageStatus.OK));
					} else {
						messageList.add(new Message("Error during creation of database dump.", MessageStatus.ERROR));
					}
					messageList.add(new Message("Add database dump to archive.", MessageStatus.OK));
					Path sqlDump = Paths.get(sqlFilePath);
					addDirToArchive(zos, sqlDump, ZIP_SQL_DUMP_PATH, false);
				}else{
					messageList.add(new Message("Skipping sql dump command as it is not configured.", MessageStatus.OK));
				}
			}
			
			// add all rulesets into zip
			if (includeRulesets){
				addFolder(zos, ConfigurationHelper.getInstance().getRulesetFolder(), false);
			}
			// add all configurations into zip
			if (includeConfiguration){
				addFolder(zos, ConfigurationHelper.getInstance().getConfigurationFolder(), false);
			}
			// add all scripts into zip
			if (includeScripts){
				addFolder(zos, ConfigurationHelper.getInstance().getScriptsFolder(), false);
			}
			// add all dockets into zip
			if (includeDockets){
				addFolder(zos, ConfigurationHelper.getInstance().getXsltFolder(), false);
			}
			// add all plugins into zip
			if (includePlugins){
				addFolder(zos, ConfigurationHelper.getInstance().getPluginFolder(), false);
			}
			// add all metadata content into zip
			if (includeMetadata){
				addFolder(zos, ConfigurationHelper.getInstance().getMetadataFolder(), true);
			}
			
			// close all connections and streams
			zos.close();
			out.flush();
			facesContext.responseComplete();
			messageList.add(new Message("Entire Goobi dump export finished successfully.", MessageStatus.OK));
			finished = true;
		} catch (IOException | InterruptedException e) {
			log.error("Exception while executing the download preparation", e);
			messageList.add(new Message("Exception while executing the download preparation: " + e.getMessage(),
					MessageStatus.ERROR));
		}
	}

	private void addFolder(ZipOutputStream zos, String inFolder, boolean isMetadataFolder) throws IOException, InterruptedException {
		numberAllFiles = getFilesCount(new File(inFolder));
		numberCurrentFile = 0;
		Path srcDir = Paths.get(inFolder);
//		addDirToArchive(zos, srcDir, "/opt/digiverso/goobi", isMetadataFolder);
		addDirToArchive(zos, srcDir, "", isMetadataFolder);
	}
	
    private int getFilesCount(File file) {
	  File[] files = file.listFiles();
	  int count = 0;
	  for (File f : files)
	    if (f.isDirectory())
	      count += getFilesCount(f);
	    else
	      count++;

	  return count;
	}
    
	/**
	 * private method to add content to zip file
	 * 
	 * @param zos
	 * @param srcFile
	 * @param parrentDirectoryName
	 */
	private void addDirToArchive(ZipOutputStream zos, Path srcFile, String parrentDirectoryName, boolean isMetadataFolder) throws IOException {
		String zipEntryName = srcFile.toFile().getName();
		if (StringUtils.isNotBlank(parrentDirectoryName)) {
			zipEntryName = parrentDirectoryName + "/" + srcFile.toFile().getName();
		}
		
		boolean ignoreThis = checkIfPathShallBeIgnored(zipEntryName, isMetadataFolder);
		if (!ignoreThis){
			if (Files.isDirectory(srcFile)) {
				messageList.add(new Message("Add folder " + zipEntryName + " to archive.", MessageStatus.OK));
				for (Path file : NIOFileUtils.listFiles(srcFile.toString())) {
					addDirToArchive(zos, file, zipEntryName, isMetadataFolder);
					continue;
				}
	
			} else {
				numberCurrentFile++;
				byte[] buffer = new byte[1024];
				FileInputStream fis = new FileInputStream(srcFile.toFile());
				zos.putNextEntry(new ZipEntry(zipEntryName));
				int length;
				while ((length = fis.read(buffer)) > 0) {
					zos.write(buffer, 0, length);
				}
				zos.closeEntry();
				fis.close();
			}
		}	
	}
	
	/**
	 * internal method to check if the given path shall be included in the zip file. If just special 
	 * IDs shall be taken or specific content shall be ignored then a file can get excluded this way
	 * 
	 * @param path
	 * @param isMetadataFolder
	 * @return
	 */
	private boolean checkIfPathShallBeIgnored(String path, boolean isMetadataFolder){
		boolean ignoreThis = false;
		
		// just do the checking for metatada folder, otherwise take it
		if (!isMetadataFolder){
			return ignoreThis;
		}
		
		// if it is the metadatafolder itself take it too
		if ((path + "/").equals(ConfigurationHelper.getInstance().getMetadataFolder())){
			return ignoreThis;
		}
		
		// just accept the IDs which are requested
		if(restrict && !restrictIDs.isEmpty()){
			boolean IdIsCorrect = false;
			String ids[] = restrictIDs.split(",");
			for (String s : ids) {
				if (path.equals(ConfigurationHelper.getInstance().getMetadataFolder() + s) || path.startsWith(ConfigurationHelper.getInstance().getMetadataFolder() + s + "/")){
					IdIsCorrect = true;
				}
			}
			if (!IdIsCorrect){
				ignoreThis = true;
				return ignoreThis;
			}
		}
		
		// ignore folders which are selected
		for (Exclude e : excludeList) {
			if (e.isUse()){
				if (path.matches(e.getRegex())){
					ignoreThis = true;
					messageList.add(new Message("Ignore file " + path + ".", MessageStatus.WARNING));	
					break;
				}else{
					messageList.add(new Message("Add file " + path + " to archive.", MessageStatus.OK));							
				}
			}
		}
		
		return ignoreThis;
	}
	
	public static void main(String[] args) throws Exception{
		XMLConfiguration config = new XMLConfiguration("/opt/digiverso/goobi/config/plugin_DumpPlugin.xml");
		Exporter e = new Exporter(config);
//		e.setRestrict(true);
//		e.setRestrictIDs("10,13,15");
//		
//		System.out.println(e.checkIfPathShallBeIgnored("/opt/digiverso/goobi/metadata/131", true));
//		System.out.println(e.checkIfPathShallBeIgnored("/opt/digiverso/goobi/metadata/131/", true));
//		System.out.println(e.checkIfPathShallBeIgnored("/opt/digiverso/goobi/metadata/131/images/abdrdevod_PPN521089360_media/00000034.tif", true));
//		System.out.println(e.checkIfPathShallBeIgnored("/opt/digiverso/goobi/metadata/13", true));
//		System.out.println(e.checkIfPathShallBeIgnored("/opt/digiverso/goobi/metadata/13/", true));
//		System.out.println(e.checkIfPathShallBeIgnored("/opt/digiverso/goobi/metadata/13/images/abdrdevod_PPN521089360_media/00000034.tif", true));
		
//		e.getExcludeList().add(new Exclude("buuu", ".*master.*", true));
//		System.out.println(e.checkIfPathShallBeIgnored("/opt/digiverso/goobi/metadata/131/images/master_abdrdevod_PPN521089360_media/00000034.tif", true));
		
		String s1 = "/opt/digiverso/goobi/metadata/131/images/master_abdrdevod_PPN521089360_tif/00000034.tif";
		System.out.println(s1.matches(".*master.*"));
		System.out.println(s1.matches(".*tif"));
		
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
