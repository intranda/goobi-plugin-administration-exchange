package de.intranda.goobi.plugins;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.faces.context.FacesContext;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IAdministrationPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.primefaces.event.FileUploadEvent;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.FacesContextHelper;
import de.sub.goobi.helper.NIOFileUtils;
import lombok.Data;
import lombok.extern.log4j.Log4j;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
@Log4j
@Data
public class DumpPlugin implements IAdministrationPlugin, IPlugin {

    private static String METADATA_FOLDER = "/opt/digiverso/goobi/metadata/";
    private static String dumpPath = "/opt/digiverso/goobi/tmp/goobi.sql";
    private static String TMP_FOLDER = "/tmp";

    private static String commandExport = "/bin/sh/export.sh";
    private static String commandImport = "/bin/sh/import.sh";
    
    private static final String PLUGIN_NAME = "DumpPlugin";
    private static final String GUI = "/uii/administration_Dump.xhtml";
    private Path importFile;
    private List<DumpMessage> messageList;
    
    /**
     * Constructor for parameter initialisation from config file
     */
    public DumpPlugin() {
    	messageList = new ArrayList<DumpMessage>();
        XMLConfiguration config = ConfigPlugins.getPluginConfig(this);
        METADATA_FOLDER = config.getString("metadataFolder", "/opt/digiverso/goobi/metadata/");
        TMP_FOLDER = config.getString("tmpFolder", "/tmp");
       
        commandExport = config.getString("commandExport", "/bin/sh/export.sh");
        commandImport = config.getString("commandImport", "/bin/sh/import.sh");
    }
    
    /**
     * public Eventhandler to allow a file upload
     * 
     * @param event
     */
    public void uploadFile(FileUploadEvent event) {
        try {
            String filename = event.getFile().getFileName();
            storeUploadedFile(filename, event.getFile().getInputstream());
        } catch (IOException e) {
            log.error("IOException while uploading the zip file", e);
            messageList.add(new DumpMessage("IOException while uploading the zip file: " + e.getMessage(), DumpMessageStatus.ERROR));
        }
        //start the unzipping
        unzipUploadedFile();
    }
    
    /**
     * internal method to store the file that was uploaded
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
            messageList.add(new DumpMessage("IOException while copying the file: " + e.getMessage(), DumpMessageStatus.ERROR));
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    log.error("Error while closing the InputStream", e);
                    messageList.add(new DumpMessage("Error while closing the InputStream: " + e.getMessage(), DumpMessageStatus.ERROR));
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    log.error("Error while closing the OutputStream", e);
                    messageList.add(new DumpMessage("Error while closing the OutputStream: " + e.getMessage(), DumpMessageStatus.ERROR));
                }
            }
        }
    }
    
    /**
     * internal method for unzipping the content of an uploaded zip file 
     */
    private void unzipUploadedFile() {
        try {
        	messageList.add(new DumpMessage("Starting to extract the uploaded ZIP file", DumpMessageStatus.OK));
            ZipInputStream zis = new ZipInputStream(new FileInputStream(importFile.toFile()));
            ZipEntry ze = zis.getNextEntry();

            while (ze != null) {
                String fileName = ze.getName();
                Path newFile = Paths.get(TMP_FOLDER, fileName);

                Files.createDirectories(newFile.getParent());

                FileOutputStream fos = new FileOutputStream(newFile.toFile());
                byte[] buffer = new byte[1024];

                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }

                fos.close();
                ze = zis.getNextEntry();
            }

            zis.closeEntry();
            zis.close();

            messageList.add(new DumpMessage("ZIP file successfully extracted.", DumpMessageStatus.OK));
        } catch (IOException e) {
            log.error("IOException while unzipping the uploaded file", e);
            messageList.add(new DumpMessage("IOException while unzipping the uploaded: " + e.getMessage(), DumpMessageStatus.ERROR));
        }

        Path sqlFolder = Paths.get(TMP_FOLDER, "tmp");
        List<String> filesInSqlFolder = NIOFileUtils.list(sqlFolder.toString());
        Path database = Paths.get(sqlFolder.toString(), filesInSqlFolder.get(0));
        messageList.add(new DumpMessage("Starting to import the uploaded SQL dump.", DumpMessageStatus.OK));
        
//        String[] command = { "/bin/sh", "-c", "mysql -u" + databaseUser + " -p" + databasePassword + " " + databaseName + " < " + database
//                .toString() };
        
        try {
            Process runtimeProcess = Runtime.getRuntime().exec(commandImport);
            int processComplete = runtimeProcess.waitFor();
            if (processComplete == 0) {
                messageList.add(new DumpMessage("SQL dump successfully imported", DumpMessageStatus.OK));
            } else {
                messageList.add(new DumpMessage("Error during creation of database dump", DumpMessageStatus.ERROR));
                return;
            }
            messageList.add(new DumpMessage("Start replacing metadata folders.", DumpMessageStatus.OK));
            messageList.add(new DumpMessage("Delete old metadata folder.", DumpMessageStatus.OK));
            FileUtils.deleteDirectory(Paths.get(METADATA_FOLDER).toFile());
            messageList.add(new DumpMessage("Old metadata folder deleted.", DumpMessageStatus.OK));
            messageList.add(new DumpMessage("Start to import new metadata folder.", DumpMessageStatus.OK));
            Path tmpMetadataFolder = Paths.get(TMP_FOLDER, METADATA_FOLDER);
            Files.move(tmpMetadataFolder, Paths.get(METADATA_FOLDER));
            messageList.add(new DumpMessage("Import successfully finished", DumpMessageStatus.OK));
        } catch (IOException | InterruptedException e) {
            log.error("Exception while importing data from zip file", e);
            messageList.add(new DumpMessage("Exception while importing data from zip file: " + e.getMessage(), DumpMessageStatus.ERROR));
        }
    }

    
    /**
     * public bean method to create the mysql dump and put all content into a zip for the download
     */
    public void downloadFile() {
        try {            
        	// create an SQL dump
            messageList.add(new DumpMessage("Creating database dump.", DumpMessageStatus.OK));
            String[] command = commandReplace(commandExport);
            Process runtimeProcess = Runtime.getRuntime().exec(command);
            int processComplete = runtimeProcess.waitFor();
            
            // check if SQL dump generation was successfull
            if (processComplete == 0) {
                messageList.add(new DumpMessage("Created SQL dump successfully", DumpMessageStatus.OK));
            } else {
                messageList.add(new DumpMessage("Error during creation of database dump", DumpMessageStatus.ERROR));
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
            messageList.add(new DumpMessage("Add database dump to archive", DumpMessageStatus.OK));
            Path sqlDump = Paths.get(dumpPath);
            addDirToArchive(zos, sqlDump, TMP_FOLDER);

            // add all metadata content into zip
            messageList.add(new DumpMessage("Add metadata folder to archive", DumpMessageStatus.OK));
            Path srcDir = Paths.get(METADATA_FOLDER);
            addDirToArchive(zos, srcDir, "/opt/digiverso/goobi");

            // close all connections and streams
            zos.close();
            out.flush();
            facesContext.responseComplete();
        } catch (IOException | InterruptedException e) {
            log.error("Exception while executing the download preparation", e);
            messageList.add(new DumpMessage("Exception while executing the download preparation: " + e.getMessage(), DumpMessageStatus.ERROR));
        }
    }
    
    /**
     * private method to add content to zip file
     * 
     * @param zos
     * @param srcFile
     * @param parrentDirectoryName
     */
    private void addDirToArchive(ZipOutputStream zos, Path srcFile, String parrentDirectoryName) {

        String zipEntryName = srcFile.toFile().getName();
        if (StringUtils.isNotBlank(parrentDirectoryName)) {
            zipEntryName = parrentDirectoryName + "/" + srcFile.toFile().getName();
        }

        if (Files.isDirectory(srcFile)) {
            messageList.add(new DumpMessage("Add folder " + zipEntryName + " to archive.", DumpMessageStatus.OK));
            for (Path file : NIOFileUtils.listFiles(srcFile.toString())) {
                addDirToArchive(zos, file, zipEntryName);
                continue;
            }

        } else {
            try {
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
            } catch (IOException e) {
                log.error("IOException happened while creating the zip file", e);
                messageList.add(new DumpMessage("IOException happened while creating the zip file: " + e.getMessage(), DumpMessageStatus.ERROR));
            }
        }
    }

    @Override
    public PluginType getType() {
        return PluginType.Administration;
    }

    @Override
    public String getTitle() {
        return PLUGIN_NAME;
    }

    @Override
    public String getGui() {
        return GUI;
    }
    
    public static void main(String[] args) throws IOException, InterruptedException{
		DumpPlugin dp = new DumpPlugin();
		
		String bla = "/bin/sh, -c, /usr/local/bin/mysqldump -u goobi -pgoobi goobi > /opt/digiverso/goobi/tmp/goobi.sql";
		String[] command1 = bla.split(", ");
		
//		String[] command1 = { "/bin/sh" , "-c", "/usr/local/bin/mysqldump -u goobi -pgoobi goobi > /opt/digiverso/goobi/tmp/goobi2.sql" };
//		String command2 = "/usr/local/bin/mysqldump -u goobi -pgoobi goobi > /opt/digiverso/goobi/tmp/goobi2.sql";
//		String command3 = "/opt/digiverso/goobi/scripts/mysql_export.sh";
        
        System.out.println("Creating database dump.");
        Process runtimeProcess = Runtime.getRuntime().exec(command1);
        int processComplete = runtimeProcess.waitFor();
        if (processComplete == 0) {
        	System.out.println("Created SQL dump successfully.");
        } else {
        	System.out.println("Error during creation of database dump: " +  processComplete);
        }
	}
    
    private String[] commandReplace(String inCommand){
    	String myCommand = inCommand.replaceAll("DATABASE_TEMPFILE", dumpPath);
    	return myCommand.split(", ");
    }

}
