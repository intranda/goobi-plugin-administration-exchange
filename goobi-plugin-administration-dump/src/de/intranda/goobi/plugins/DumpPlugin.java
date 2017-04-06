package de.intranda.goobi.plugins;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import net.xeoh.plugins.base.annotations.PluginImplementation;
import lombok.Data;
import lombok.extern.log4j.Log4j;

@PluginImplementation
@Log4j
@Data
public class DumpPlugin implements IAdministrationPlugin, IPlugin {

    private static String METADATA_FOLDER = "/opt/digiverso/goobi/metadata/";
    private static String TMP_FOLDER = "/tmp";
    private static String databaseName = "goobi";
    private static String databaseUser = "goobi";
    private static String databasePassword = "goobi";
    private static String commandExport = "/bin/sh/export.sh";
    private static String commandImport = "/bin/sh/import.sh";
    
    private static final String PLUGIN_NAME = "DumpPlugin";
    private static final String GUI = "/uii/administration_Dump.xhtml";
    private Path importFile;
    private String message = "";

    public DumpPlugin() {
        XMLConfiguration config = ConfigPlugins.getPluginConfig(this);
        METADATA_FOLDER = config.getString("metadataFolder", "/opt/digiverso/goobi/metadata/");
        TMP_FOLDER = config.getString("tmpFolder", "/tmp");
        databaseName = config.getString("databaseName", "goobi");
        databaseUser = config.getString("databaseUser", "goobi");
        databasePassword = config.getString("databasePassword", "goobi");
        commandExport = config.getString("commandExport", "/usr/local/bin/mysqldump -u [USER] -p[PASSWORD] [DATABASE] > [TEMPFILE]");
        commandImport = config.getString("commandImport", "/usr/local/bin/mysqldump -u [USER] -p[PASSWORD] [DATABASE] > [TEMPFILE]");
    }

    public void handleFileUpload(FileUploadEvent event) {
        try {
            String filename = event.getFile().getFileName();
            copyFile(filename, event.getFile().getInputstream());

        } catch (IOException e) {
            log.error(e);
        }

        unzipUploadedFile();
    }

    private void unzipUploadedFile() {
        try {
            message = "Unzipping uploaded file.<br/>";
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

            message += "Extracted files.<br/>";
        } catch (IOException e) {
            log.error(e);
        }

        Path sqlFolder = Paths.get(TMP_FOLDER, "tmp");
        List<String> filesInSqlFolder = NIOFileUtils.list(sqlFolder.toString());
        Path database = Paths.get(sqlFolder.toString(), filesInSqlFolder.get(0));
        message += "Import sql dump.<br/>";

        String myCommand = commandImport.replaceAll("DATABASE_USER", databaseUser);
        myCommand = myCommand.replaceAll("DATABASE_PASSWORD", databasePassword);
        myCommand = myCommand.replaceAll("DATABASE_NAME", databaseName);
        myCommand = myCommand.replaceAll("DATABASE_TEMPFILE", database.toString());
        
//        String[] command = { "/bin/sh", "-c", "mysql -u" + databaseUser + " -p" + databasePassword + " " + databaseName + " < " + database
//                .toString() };
        
        try {
            Process runtimeProcess = Runtime.getRuntime().exec(myCommand);
            int processComplete = runtimeProcess.waitFor();
            if (processComplete == 0) {
                message += "Created database dump.<br/>";
            } else {
                message += "Error during creation of database dump.";
                return;
            }

            message += "Sql dump imported.<br/>";

            message += "Replacing metadata folder.<br/>";

            message += "Delete old metadata folder.<br/>";
            FileUtils.deleteDirectory(Paths.get(METADATA_FOLDER).toFile());
            message += "Import new metadata folder.<br/>";
            Path tmpMetadataFolder = Paths.get(TMP_FOLDER, METADATA_FOLDER);
            Files.move(tmpMetadataFolder, Paths.get(METADATA_FOLDER));
            message += "Finished import.<br/>";

        } catch (IOException | InterruptedException e) {
            log.error(e);
        }
    }

    public void copyFile(String fileName, InputStream in) {
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
            log.error(e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    log.error(e);
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    log.error(e);
                }
            }

        }

    }

    public void downloadDump() {

        FacesContext facesContext = FacesContextHelper.getCurrentFacesContext();
        HttpServletResponse response = (HttpServletResponse) facesContext.getExternalContext().getResponse();

        message = "Creating database dump.<br/>";

        try {

            Path database = Files.createTempFile("goobi", ".sql");
            
            String myCommand = commandExport.replaceAll("DATABASE_USER", databaseUser);
            myCommand = myCommand.replaceAll("DATABASE_PASSWORD", databasePassword);
            myCommand = myCommand.replaceAll("DATABASE_NAME", databaseName);
            myCommand = myCommand.replaceAll("DATABASE_TEMPFILE", "/opt/digiverso/goobi/tmp/dump.sql");
            
//            String[] command = { "/bin/sh", "-c", "mysqldump -u" + databaseUser + " -p" + databasePassword + " " + databaseName + " > " + database
//                    .toString() };
            
            
            Process runtimeProcess = Runtime.getRuntime().exec(myCommand);
            int processComplete = runtimeProcess.waitFor();
            if (processComplete == 0) {
                message += "Created database dump.<br/>";
            } else {
                message += "Error during creation of database dump.";
                return;
            }

            OutputStream out = response.getOutputStream();

            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", "attachment;filename=\"goobidump.zip\"");
            ZipOutputStream zos = new ZipOutputStream(out);

            message += "Add database dump to archive.<br/>";

            addDirToArchive(zos, database, TMP_FOLDER);

            message += "Add metadata folder to archive.<br/>";

            Path srcDir = Paths.get(METADATA_FOLDER);
            addDirToArchive(zos, srcDir, "/opt/digiverso/goobi");

            zos.close();
            out.flush();
            facesContext.responseComplete();
        } catch (IOException | InterruptedException e) {
            log.error(e);
        }

    }

    private void addDirToArchive(ZipOutputStream zos, Path srcFile, String parrentDirectoryName) {

        String zipEntryName = srcFile.toFile().getName();
        if (StringUtils.isNotBlank(parrentDirectoryName)) {
            zipEntryName = parrentDirectoryName + "/" + srcFile.toFile().getName();
        }

        if (Files.isDirectory(srcFile)) {

            message += "Add folder " + zipEntryName + " to archive.<br/>";
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
            } catch (IOException ioe) {
                log.error(ioe);
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

}
