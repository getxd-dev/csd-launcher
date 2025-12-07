/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.swing;

import com.skcraft.launcher.LauncherUtils;
import lombok.extern.java.Log;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.logging.Level;

import static com.skcraft.launcher.LauncherUtils.checkInterrupted;
import static org.apache.commons.io.IOUtils.closeQuietly;

@Log
public final class WebpagePanel extends JPanel {
    
    private final WebpagePanel self = this;
    
    private URL url;
    private boolean activated;
    private Browser browser;
    private Border browserBorder;
    
    public static WebpagePanel forURL(URL url, boolean lazy) {
        return new WebpagePanel(url, lazy);
    }
    
    public static WebpagePanel forHTML(String html) {
        return new WebpagePanel(html);
    }

    private WebpagePanel(URL url, boolean lazy) {
        this.url = url;
        
        setLayout(new BorderLayout());
        
        if (lazy) {
            setPlaceholder();
        } else {
            setDocument();
            browse(url, false);
        }
    }

    private WebpagePanel(String text) {
        this.url = null;
        
        setLayout(new BorderLayout());
        
        setDocument();
        if (browser != null) {
            browser.setText(text);
        }
    }
    
    public WebpagePanel(boolean lazy) {
        this.url = null;
        
        setLayout(new BorderLayout());

        if (lazy) {
            setPlaceholder();
        } else {
            setDocument();
        }
    }

    public Border getBrowserBorder() {
        return browserBorder;
    }

    public void setBrowserBorder(Border browserBorder) {
        synchronized (this) {
            this.browserBorder = browserBorder;
            if (browser != null) {
                browser.setBorder(browserBorder);
            }
        }
    }

    private void setDocument() {
        activated = true;
        
        try {
            browser = new FxBrowser();
        } catch (Throwable t) {
            log.log(Level.INFO, "JavaFX not available, falling back to Swing", t);
            browser = new SwingBrowser();
        }

        if (browserBorder != null) {
            browser.setBorder(browserBorder);
        }

        SwingHelper.removeOpaqueness(this);
        add(browser.getComponent(), BorderLayout.CENTER);
    }
    
    private void setPlaceholder() {
        activated = false;
        
        JLayeredPane panel = new JLayeredPane();
        panel.setBorder(new CompoundBorder(
                BorderFactory.createEtchedBorder(), BorderFactory
                        .createEmptyBorder(4, 4, 4, 4)));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        
        final JButton showButton = new JButton("Load page");
        showButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        showButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showButton.setVisible(false);
                setDocument();
                browse(url, false);
            }
        });
        
        // Center the button vertically.
        panel.add(new Box.Filler(
                new Dimension(0, 0),
                new Dimension(0, 0),
                new Dimension(1000, 1000)));
        panel.add(showButton);
        panel.add(new Box.Filler(
                new Dimension(0, 0),
                new Dimension(0, 0),
                new Dimension(1000, 1000)));
        
        add(panel, BorderLayout.CENTER);
    }
    
    /**
     * Browse to a URL.
     * 
     * @param url the URL
     * @param onlyChanged true to only browse if the last URL was different
     * @return true if only the URL was changed
     */
    public boolean browse(URL url, boolean onlyChanged) {
        if (onlyChanged && this.url != null && this.url.equals(url)) {
            return false;
        }
        
        this.url = url;
        
        if (activated && browser != null && url != null) {
            browser.browse(url);
        }
        
        return true;
    }

    private interface Browser {
        JComponent getComponent();
        void browse(URL url);
        void setText(String text);
        void setBorder(Border border);
    }

    private class FxBrowser implements Browser {
        private final JComponent jfxPanel;
        private final Method loadMethod;
        private final Method loadContentMethod;
        private Object engine;

        FxBrowser() throws Exception {
            // Reflection to avoid compile-time dependency on JavaFX
            Class<?> jfxPanelClass = Class.forName("javafx.embed.swing.JFXPanel");
            jfxPanel = (JComponent) jfxPanelClass.getConstructor().newInstance();

            // Prevent implicitly closing the application if the window closes (Swing handles lifecycle)
            try {
                Class<?> platformClass = Class.forName("javafx.application.Platform");
                Method setImplicitExit = platformClass.getMethod("setImplicitExit", boolean.class);
                setImplicitExit.invoke(null, false);
            } catch (Exception ignored) {}

            runLater(() -> {
                try {
                    Class<?> webViewClass = Class.forName("javafx.scene.web.WebView");
                    Object webView = webViewClass.getConstructor().newInstance();

                    Class<?> sceneClass = Class.forName("javafx.scene.Scene");
                    Constructor<?> sceneCtor = sceneClass.getConstructor(Class.forName("javafx.scene.Parent"));
                    Object scene = sceneCtor.newInstance(webView);

                    jfxPanelClass.getMethod("setScene", sceneClass).invoke(jfxPanel, scene);

                    engine = webViewClass.getMethod("getEngine").invoke(webView);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            Class<?> webEngineClass = Class.forName("javafx.scene.web.WebEngine");
            loadMethod = webEngineClass.getMethod("load", String.class);
            loadContentMethod = webEngineClass.getMethod("loadContent", String.class);
        }

        private void runLater(Runnable r) throws Exception {
            Class<?> platformClass = Class.forName("javafx.application.Platform");
            Method runLater = platformClass.getMethod("runLater", Runnable.class);
            runLater.invoke(null, r);
        }

        @Override
        public JComponent getComponent() {
            return jfxPanel;
        }

        @Override
        public void browse(final URL url) {
            try {
                runLater(() -> {
                    try {
                        if (engine != null) {
                            loadMethod.invoke(engine, url.toString());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void setText(final String text) {
            try {
                runLater(() -> {
                    try {
                        if (engine != null) {
                            loadContentMethod.invoke(engine, text);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void setBorder(Border border) {
            jfxPanel.setBorder(border);
        }
    }

    private class SwingBrowser implements Browser {
        private JEditorPane documentView;
        private JScrollPane documentScroll;
        private JProgressBar progressBar;
        private Thread thread;
        private final JLayeredPane panel;

        SwingBrowser() {
            panel = new JLayeredPane();
            panel.setLayout(new WebpageLayoutManager());

            documentView = new JEditorPane();
            documentView.setOpaque(false);
            documentView.setBorder(null);
            documentView.setEditable(false);
            documentView.addHyperlinkListener(new HyperlinkListener() {
                @Override
                public void hyperlinkUpdate(HyperlinkEvent e) {
                    if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                        if (e.getURL() != null) {
                            SwingHelper.openURL(e.getURL(), self);
                        }
                    }
                }
            });

            documentScroll = new JScrollPane(documentView);
            documentScroll.setOpaque(false);
            panel.add(documentScroll, new Integer(1));
            documentScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

            progressBar = new JProgressBar();
            progressBar.setIndeterminate(true);
            panel.add(progressBar, new Integer(2));

            SwingHelper.removeOpaqueness(documentView);
            SwingHelper.removeOpaqueness(documentScroll);
        }

        @Override
        public JComponent getComponent() {
            return panel;
        }

        @Override
        public void browse(URL url) {
            if (thread != null) {
                thread.interrupt();
            }

            progressBar.setVisible(true);

            thread = new Thread(new FetchWebpage(url));
            thread.setDaemon(true);
            thread.start();
        }

        @Override
        public void setText(String text) {
            setDisplay(text, null);
        }

        @Override
        public void setBorder(Border border) {
            if (documentScroll != null) {
                documentScroll.setBorder(border);
            }
        }

        private void setDisplay(String text, URL baseUrl) {
            progressBar.setVisible(false);
            documentView.setContentType("text/html");
            HTMLDocument document = (HTMLDocument) documentView.getDocument();

            // Force modern font style
            StyleSheet styleSheet = ((HTMLEditorKit) documentView.getEditorKit()).getStyleSheet();
            styleSheet.addRule("body { font-family: sans-serif; color: #f0f0f0; margin: 20px; }");
            styleSheet.addRule("h1, h2, h3 { text-shadow: 2px 2px 4px #000000; }");
            styleSheet.addRule("p, li { text-shadow: 1px 1px 2px #000000; font-size: 1.1em; }");

            // Clear existing styles
            Enumeration<?> e = document.getStyleNames();
            while (e.hasMoreElements()) {
                Object o = e.nextElement();
                document.removeStyle((String) o);
            }

            document.setBase(baseUrl);
            documentView.setText(text);

            documentView.setCaretPosition(0);
        }

        private void setError(String text) {
            progressBar.setVisible(false);
            documentView.setContentType("text/plain");
            documentView.setText(text);
            documentView.setCaretPosition(0);
        }

        private class FetchWebpage implements Runnable {
            private URL url;

            public FetchWebpage(URL url) {
                this.url = url;
            }

            @Override
            public void run() {
                HttpURLConnection conn = null;

                try {
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setUseCaches(false);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Java) SKMCLauncher");
                    conn.setDoInput(true);
                    conn.setDoOutput(false);
                    conn.setReadTimeout(5000);

                    conn.connect();

                    checkInterrupted();

                    if (conn.getResponseCode() != 200) {
                        throw new IOException(
                                "Did not get expected 200 code, got "
                                        + conn.getResponseCode());
                    }

                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(),
                                    "UTF-8"));

                    StringBuilder s = new StringBuilder();
                    char[] buf = new char[1024];
                    int len = 0;
                    while ((len = reader.read(buf)) != -1) {
                        s.append(buf, 0, len);
                    }
                    String result = s.toString();

                    checkInterrupted();

                    final URL baseUrl = LauncherUtils.concat(url, "");
                    SwingUtilities.invokeLater(() -> setDisplay(result, baseUrl));
                } catch (IOException e) {
                    if (Thread.interrupted()) {
                        return;
                    }

                    log.log(Level.WARNING, "Failed to fetch page", e);
                    SwingUtilities.invokeLater(() -> setError("Failed to fetch page: " + e.getMessage()));
                } catch (InterruptedException e) {
                } finally {
                    if (conn != null)
                        conn.disconnect();
                    conn = null;
                }
            }
        }
    }

}