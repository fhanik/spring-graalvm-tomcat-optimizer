package org.springframework.graalvm.maven;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

/**
 * Goal which touches a timestamp file.
 */
@Mojo(name = "spring-graalvm-optimize-jar", defaultPhase = LifecyclePhase.PACKAGE, requiresProject = true, threadSafe = true,
    requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
    requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class BootOptimizerForApacheTomcat
    extends AbstractMojo {
    /**
     * Location of the file.
     */
    @Parameter(defaultValue = "${project.build.directory}", property = "outputDir", required = true)
    private File outputDirectory;

    /**
     * Name of the generated archive.
     */
    @Parameter(defaultValue = "${project.build.finalName}", readonly = true)
    private String finalName;

    /**
     * The Maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    /**
     * Maven project helper utils.
     */
    @Component
    protected MavenProjectHelper projectHelper;

    private Map<String, String> fileSystemProps = new HashMap<>();

    public BootOptimizerForApacheTomcat() {
        fileSystemProps.put("update", "true");
        fileSystemProps.put("create", "false");
    }

    public void execute()
        throws MojoExecutionException {
        File f = outputDirectory;

        if (!f.exists()) {
            f.mkdirs();
        }

        File testMojoFile = new File(f, "testMojoFile.txt");

        FileWriter w = null;
        try {
            w = new FileWriter(testMojoFile);
            w.write(this.finalName+".jar\n");

            File bootJar = new File(this.outputDirectory, this.finalName+".jar");
            w.write("File exists:"+bootJar.exists()+"\n");


            System.err.println("FILIP 1");
            URI uri = null;
            try {
                uri = new URI("jar:file:"+bootJar.getAbsolutePath());
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
            System.err.println("FILIP 1.5: "+uri.toString());
            try (FileSystem zipfs = FileSystems.newFileSystem(uri, fileSystemProps)) {
                System.err.println("FILIP 2");
                Path pathInZipfile = zipfs.getPath("/BOOT-INF/lib/tomcat-embed-programmatic-9.0.38-dev.jar");
                System.err.println("FILIP 3");
                File tempFile = new File(this.outputDirectory, "temp-tomcat-embed.jar");
                if (tempFile.exists()) {
                    tempFile.delete();
                }
                System.err.println("FILIP 3.1: Tempfile exists: "+tempFile.exists());
                Path tempFilePath = Paths.get(tempFile.getAbsolutePath());
                Files.copy(pathInZipfile, tempFilePath);
                System.err.println("FILIP 3.2: Tempfile exists: "+tempFile.exists());
                processTomcatOptimizations(tempFile);
                System.err.println("FILIP 4.1: Attempting to replace a file in the ZIP");
                // copy a file into the zip file
                Files.copy(tempFilePath, pathInZipfile, StandardCopyOption.REPLACE_EXISTING);
                System.err.println("FILIP 4.2: File replace complete");
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Error creating file " + testMojoFile, e);
        } finally {
            if (w != null) {
                try {
                    w.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    private void processTomcatOptimizations(File tempFilePath) {
        URI uri;
        try {
            uri = new URI("jar:file:"+tempFilePath.getAbsolutePath());
            try (FileSystem zipfs = FileSystems.newFileSystem(uri, fileSystemProps)) {
                Path path = zipfs.getPath("/META-INF/native-image/org.apache.tomcat.embed/tomcat-embed-programmatic/native-image.properties");
                Files.delete(path);
                System.err.println("FILIP EX: File deleted: "+path.toString());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }
}
