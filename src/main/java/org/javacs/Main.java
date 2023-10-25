package org.javacs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.javacs.lsp.LSP;

public class Main {
  private static final Logger LOG = Logger.getLogger("main");

  public static void setRootFormat() {
    var root = Logger.getLogger("");

    for (var h : root.getHandlers()) {
      h.setFormatter(new LogFormat());
    }
  }

  public static class Lolol extends com.sun.tools.javac.util.Context {

  }

  public static void main(String[] args) throws Throwable {
    Lolol l = new Lolol();
    LOG.setLevel(Level.FINE);
    com.sun.tools.javac.api.JavacTool jct = com.sun.tools.javac.api.JavacTool.create();

    boolean quiet = Arrays.stream(args).anyMatch("--quiet"::equals);

    if (quiet) {
      LOG.setLevel(Level.OFF);
    }

    int port = -1;
    for (int i = 0; i < args.length; i++) {
      System.out.println(args[i]);
      if ("port".equals(args[i])) {
        port = Integer.valueOf(args[i + 1]);
        System.out.println(String.format("Starting server. listening on port %d", port));
      }
    }
    final int fp = port;

    new java.lang.Thread(new Runnable() {

      @Override
      public void run() {

        try {
          System.out.println(String.format("Starting server. listening on port %d", fp));
          final ServerSocket ss = new ServerSocket(fp);
          System.out.println("waiting for connection");
          final Socket s = ss.accept();
          System.out.println("Got connection");
          final InputStream is = s.getInputStream();
          final OutputStream os = s.getOutputStream();

          System.out.println("Starting connection");
          LSP.connect(JavaLanguageServer::new, is, os);
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }

      }
    }).start();
    System.out.println("JLS thread started.");
  }

}
