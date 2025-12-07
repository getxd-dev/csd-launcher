/*
 * SKCraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher;

import com.google.common.base.Supplier;
import com.skcraft.launcher.swing.SwingHelper;
import lombok.extern.java.Log;
import org.pushingpixels.substance.api.SubstanceLookAndFeel;
import org.pushingpixels.substance.api.fonts.FontPolicy;
import org.pushingpixels.substance.api.fonts.FontSet;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.util.logging.Level;

@Log
public class FancyLauncher {

    public static void main(final String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        System.setProperty("sun.awt.noerasebackground", "true");
        System.setProperty("substancelaf.windowRoundedCorners", "true");

        Launcher.setupLogger();

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.currentThread().setContextClassLoader(FancyLauncher.class.getClassLoader());
                    UIManager.getLookAndFeelDefaults().put("ClassLoader", FancyLauncher.class.getClassLoader());
                    UIManager.getDefaults().put("SplitPane.border", BorderFactory.createEmptyBorder());
                    JFrame.setDefaultLookAndFeelDecorated(true);
                    JDialog.setDefaultLookAndFeelDecorated(true);

                    boolean substanceLoaded = SwingHelper.setLookAndFeel("com.skcraft.launcher.skin.LauncherLookAndFeel");
                    if (!substanceLoaded) {
                        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                    }

                    setModernFont();

                    if (!SwingHelper.setLookAndFeel("com.skcraft.launcher.skin.LauncherLookAndFeel")) {
                        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                    }

                    Launcher launcher = Launcher.createFromArguments(args);
                    launcher.setMainWindowSupplier(new CustomWindowSupplier(launcher));
                    launcher.showLauncherWindow();
                } catch (Throwable t) {
                    log.log(Level.WARNING, "Load failure", t);
                    SwingHelper.showErrorDialog(null, "Uh oh! The updater couldn't be opened because a " +
                            "problem was encountered.", "Launcher error", t);
                }
            }
        });
    }
    private static void setModernFont() {
        // Try to set a modern font (Segoe UI on Windows, otherwise generic Sans Serif)
        final FontUIResource font = new FontUIResource("Segoe UI", Font.PLAIN, 13);
        final FontUIResource titleFont = new FontUIResource("Segoe UI", Font.BOLD, 13);

        // Fallback if Segoe UI is missing (non-Windows)
        final FontUIResource controlFont = font.getFamily().equals("Dialog") ?
                new FontUIResource(Font.SANS_SERIF, Font.PLAIN, 13) : font;
        final FontUIResource titleControlFont = titleFont.getFamily().equals("Dialog") ?
                new FontUIResource(Font.SANS_SERIF, Font.BOLD, 13) : titleFont;
        // Apply to Substance Policy
        SubstanceLookAndFeel.setFontPolicy(new FontPolicy() {
            @Override
            public FontSet getFontSet(String lafName, UIDefaults table) {
                return new FontSet() {
                    public FontUIResource getControlFont() { return controlFont; }
                    public FontUIResource getMenuFont() { return controlFont; }
                    public FontUIResource getTitleFont() { return titleControlFont; }
                    public FontUIResource getWindowTitleFont() { return titleControlFont; }
                    public FontUIResource getSmallFont() { return controlFont; }
                    public FontUIResource getMessageFont() { return controlFont; }
                };
            }
        });

        // Apply to standard UIManager for all possible keys
        // We set this BEFORE LookAndFeel install to ensure it takes precedence
        java.util.Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
            if (value instanceof javax.swing.plaf.FontUIResource) {
                UIManager.put(key, controlFont);
            }
        }
    }

    private static class CustomWindowSupplier implements Supplier<Window> {

        private final Launcher launcher;

        private CustomWindowSupplier(Launcher launcher) {
            this.launcher = launcher;
        }

        @Override
        public Window get() {
            return new FancyLauncherFrame(launcher);
        }
    }

}
