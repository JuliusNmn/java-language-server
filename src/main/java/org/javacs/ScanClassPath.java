package org.javacs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.javacs.guava.ClassPath;

class ScanClassPath {

    // TODO delete this and implement findPublicTypeDeclarationInJdk some other way
    /** All exported modules that are present in JDK 10 or 11 */
    static String[] JDK_MODULES = {
        "java.activation",
        "java.base",
        "java.compiler",
        "java.corba",
        "java.datatransfer",
        "java.desktop",
        "java.instrument",
        "java.jnlp",
        "java.logging",
        "java.management",
        "java.management.rmi",
        "java.naming",
        "java.net.http",
        "java.prefs",
        "java.rmi",
        "java.scripting",
        "java.se",
        "java.se.ee",
        "java.security.jgss",
        "java.security.sasl",
        "java.smartcardio",
        "java.sql",
        "java.sql.rowset",
        "java.transaction",
        "java.transaction.xa",
        "java.xml",
        "java.xml.bind",
        "java.xml.crypto",
        "java.xml.ws",
        "java.xml.ws.annotation",
        "javafx.base",
        "javafx.controls",
        "javafx.fxml",
        "javafx.graphics",
        "javafx.media",
        "javafx.swing",
        "javafx.web",
        "jdk.accessibility",
        "jdk.aot",
        "jdk.attach",
        "jdk.charsets",
        "jdk.compiler",
        "jdk.crypto.cryptoki",
        "jdk.crypto.ec",
        "jdk.dynalink",
        "jdk.editpad",
        "jdk.hotspot.agent",
        "jdk.httpserver",
        "jdk.incubator.httpclient",
        "jdk.internal.ed",
        "jdk.internal.jvmstat",
        "jdk.internal.le",
        "jdk.internal.opt",
        "jdk.internal.vm.ci",
        "jdk.internal.vm.compiler",
        "jdk.internal.vm.compiler.management",
        "jdk.jartool",
        "jdk.javadoc",
        "jdk.jcmd",
        "jdk.jconsole",
        "jdk.jdeps",
        "jdk.jdi",
        "jdk.jdwp.agent",
        "jdk.jfr",
        "jdk.jlink",
        "jdk.jshell",
        "jdk.jsobject",
        "jdk.jstatd",
        "jdk.localedata",
        "jdk.management",
        "jdk.management.agent",
        "jdk.management.cmm",
        "jdk.management.jfr",
        "jdk.management.resource",
        "jdk.naming.dns",
        "jdk.naming.rmi",
        "jdk.net",
        "jdk.pack",
        "jdk.packager.services",
        "jdk.rmic",
        "jdk.scripting.nashorn",
        "jdk.scripting.nashorn.shell",
        "jdk.sctp",
        "jdk.security.auth",
        "jdk.security.jgss",
        "jdk.snmp",
        "jdk.unsupported",
        "jdk.unsupported.desktop",
        "jdk.xml.dom",
        "jdk.zipfs",
    };

  static Set<String> jdkTopLevelClasses() {
    LOG.info("Searching for top-level classes in the JDK. usrer home=" + System.getenv("HOME"));

    Set<String> classes;
    File cacheFile = new File(System.getenv("JAVA_HOME"), "jdkTopLevelClasses.zip");
    if (!cacheFile.exists()) {
      new File(System.getenv("HOME"), "jdkTopLevelClasses.zip");
    }

    if (cacheFile.exists()) {
      LOG.info("Loading classes from cache file at " + cacheFile);
      try (ZipFile zipFile = new ZipFile(cacheFile)) {
        ZipEntry entry = zipFile.getEntry("jdkTopLevelClasses.cache");
        if (entry != null) {
          LOG.info("Cache entry found.");
          InputStream inputStream = zipFile.getInputStream(entry);
          ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
          classes = (HashSet<String>) objectInputStream.readObject();
          objectInputStream.close();
          inputStream.close();
        } else {
          LOG.info("Cache entry not found. Generating classes.");
          // If the entry does not exist in the ZIP file, generate the classes
          classes = generateJDKTopLevelClasses();
          LOG.info("Saving classes to cache file.");
          saveToZipCache(classes, cacheFile);
        }
      } catch (IOException | ClassNotFoundException e) {
        LOG.warning("Cache loading failed. Falling back to generating the classes.");
        // Handle exceptions if cache loading fails, and fall back to generating the
        // classes
        classes = generateJDKTopLevelClasses();
      }
    } else {
      LOG.info("Cache file does not exist. Generating classes.");
      // Cache file does not exist, generate the classes and save them to the ZIP file
      classes = generateJDKTopLevelClasses();
      LOG.info("Saving classes to cache file.");
      saveToZipCache(classes, cacheFile);
    }

    LOG.info(String.format("Found %d classes in the java platform", classes.size()));

    return classes;
  }

  private static Set<String> generateJDKTopLevelClasses() {
    LOG.info("Generating JDK top-level classes.");
    var classes = new HashSet<String>();
    var fs = FileSystems.getFileSystem(URI.create("jrt:/"));
    for (var m : JDK_MODULES) {
      var moduleRoot = fs.getPath(String.format("/modules/%s/", m));

      try (var stream = Files.walk(moduleRoot)) {
        var it = stream.iterator();
        while (it.hasNext()) {
          var classFile = it.next();
          var relative = moduleRoot.relativize(classFile).toString();
          if (relative.endsWith(".class") && !relative.contains("$")) {
            var trim = relative.substring(0, relative.length() - ".class".length());
            var qualifiedName = trim.replace(File.separatorChar, '.');
            classes.add(qualifiedName);
          }
        }
      } catch (IOException e) {
        // e.printStackTrace();
        // LOG.warning("Failed to index module " + m + "(" + e.getMessage() + ")");
        // Handle exceptions if needed
      }
    }
    LOG.info("Generation complete.");
    return classes;
  }

  private static void saveToZipCache(Set<String> classes, File cacheFile) {
    LOG.info("Saving classes to cache file. " + cacheFile.getPath());
    try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(cacheFile))) {
      zipOutputStream.putNextEntry(new ZipEntry("jdkTopLevelClasses.cache"));
      ObjectOutputStream objectOutputStream = new ObjectOutputStream(zipOutputStream);
      objectOutputStream.writeObject(classes);
      objectOutputStream.close();
      zipOutputStream.closeEntry();
    } catch (IOException e) {
      e.printStackTrace();
      LOG.warning("Cache saving failed.");
      // Handle exceptions if cache saving fails, but still return the generated
      // classes
    }
    LOG.info("Cache saving complete.");
  }
  /*
   * static Set<String> jdkTopLevelClasses() {
   * LOG.info("Searching for top-level classes in the JDK");
   * 
   * var classes = new HashSet<String>(); var fs =
   * FileSystems.getFileSystem(URI.create("jrt:/")); for (var m : JDK_MODULES) {
   * var moduleRoot = fs.getPath(String.format("/modules/%s/", m)); try (var
   * stream = Files.walk(moduleRoot)) { var it = stream.iterator(); while
   * (it.hasNext()) { var classFile = it.next(); var relative =
   * moduleRoot.relativize(classFile).toString(); if (relative.endsWith(".class")
   * && !relative.contains("$")) { var trim = relative.substring(0,
   * relative.length() - ".class".length()); var qualifiedName =
   * trim.replace(File.separatorChar, '.'); classes.add(qualifiedName); } } }
   * catch (IOException e) { // LOG.log(Level.WARNING, "Failed indexing module " +
   * m + "(" + e.getMessage() + // ")"); } }
   * 
   * LOG.info(String.format("Found %d classes in the java platform",
   * classes.size()));
   * 
   * return classes; }
   */

  static Set<String> classPathTopLevelClasses(Set<Path> classPath) {
    LOG.info(String.format("Searching for top-level classes in %d classpath locations", classPath.size()));

    var urls = classPath.stream().map(ScanClassPath::toUrl).toArray(URL[]::new);
    var classLoader = new URLClassLoader(urls, null);
    ClassPath scanner;
    try {
      scanner = ClassPath.from(classLoader);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    var classes = new HashSet<String>();
    for (var c : scanner.getTopLevelClasses()) {
      classes.add(c.getName());
    }

    LOG.info(String.format("Found %d classes in classpath", classes.size()));

    return classes;
  }

  private static URL toUrl(Path p) {
    try {
      return p.toUri().toURL();
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  private static final Logger LOG = Logger.getLogger("main");
}
