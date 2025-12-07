/*
 * SKCraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher;

import com.skcraft.launcher.Instance;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.auth.AccountList;
import com.skcraft.launcher.auth.SavedSession;
import com.skcraft.launcher.auth.Session;
import com.skcraft.launcher.dialog.AccountSelectDialog;
import com.skcraft.launcher.dialog.LauncherFrame;
import com.skcraft.launcher.dialog.component.BetterComboBox;
import com.skcraft.launcher.swing.SwingHelper;
import com.skcraft.launcher.swing.WebpagePanel;
import lombok.NonNull;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;

public class FancyLauncherFrame extends LauncherFrame {

    private final Launcher launcher;
    private final JComboBox<Instance> instanceSelector = new BetterComboBox<>();
    private final JLabel nameLabel = new JLabel();
    private final JLabel headLabel = new JLabel();
    private JPanel container;

    // Icons
    private final Icon instanceIcon = SwingHelper.createIcon(Launcher.class, "instance_icon.png", 16, 16);
    private final Icon downloadIcon = SwingHelper.createIcon(Launcher.class, "download_icon.png", 16, 16);

    // Custom colors
    private static final Color GLASS_COLOR = new Color(10, 10, 10, 140);
    private static final Color HOVER_COLOR = new Color(255, 255, 255, 20);

    /**
     * Create a new frame.
     *
     * @param launcher the launcher
     */
    public FancyLauncherFrame(@NonNull Launcher launcher) {
        super(launcher);
        this.launcher = launcher;

        setSize(850, 550);
        setLocationRelativeTo(null);

        // We rebuild the UI entirely for the fancy version
        container.removeAll();
        // Fixed layout rows to prevent cutoff: [Header][Content][Footer]
        container.setLayout(new MigLayout("fill, insets 0, gap 0", "[grow]", "[60!][grow][60!]"));

        // 1. Top Bar (Logo + Account Manager)
        JPanel topBar = new GlassPanel(new MigLayout("fill, insets 10 20 10 20", "[][grow][right]", "[]"));
        // Logo
        JLabel logoLabel = new JLabel("Changelogs");
        logoLabel.setFont(logoLabel.getFont().deriveFont(Font.BOLD, 18f));
        logoLabel.setForeground(Color.WHITE);
        
        // Account Manager
        JPanel accountPanel = createAccountPanel();
        updateAccountInfo(); // Populate initial data

        topBar.add(logoLabel);
        topBar.add(new JLabel("")); // Spacer
        topBar.add(accountPanel);

        // 2. Center (Webpage) - Now full width
        WebpagePanel webView = createNewsPanel();
        webView.setOpaque(false);

        // 3. Bottom Bar (Controls)
        // Columns: [Refresh] [SelfUpdate] [Checkbox] [Selector(Grow)] [Options] [Launch]
        JPanel bottomBar = new GlassPanel(new MigLayout("fill, insets 10 20 10 20", "[][][][grow][][]", "[]"));

        // Style the Options button to be minimalist (Icon only)
        optionsButton.setText("Settings"); // Unicode Gear Icon
        optionsButton.setFont(optionsButton.getFont().deriveFont(Font.BOLD, 12f));
        optionsButton.setToolTipText("Launcher Options");
        optionsButton.setContentAreaFilled(false);
        optionsButton.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        optionsButton.setForeground(new Color(200, 200, 200));
        optionsButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Configure Instance Selector
        DefaultComboBoxModel<Instance> model = new DefaultComboBoxModel<>();
        instanceSelector.setModel(model);
        instanceSelector.setRenderer(new InstanceComboRenderer());
        instanceSelector.addActionListener(e -> {
            // Sync selection with the invisible table so base logic works
            int index = instanceSelector.getSelectedIndex();
            if (index >= 0 && index < getInstancesTable().getRowCount()) {
                getInstancesTable().setRowSelectionInterval(index, index);
            }
            // Update button text based on state
            updateLaunchButton();
        });

        // Add listener to auto-refresh the dropdown when instances load
        getInstancesTable().getModel().addTableModelListener(e -> updateInstanceList());
        updateInstanceList(); // Initial population

        bottomBar.add(refreshButton);
        bottomBar.add(selfUpdateButton, "gapleft 5, hidemode 3");
        bottomBar.add(updateCheck, "gapleft 10");
        bottomBar.add(instanceSelector, "growx, width 200:300:400, gapright 10");
        bottomBar.add(optionsButton);
        bottomBar.add(launchButton, "w 100!, h 32!");

        // Assemble using explicit cells to prevent overlapping/cutoff
        container.add(topBar, "cell 0 0, grow");
        container.add(webView, "cell 0 1, grow");
        container.add(bottomBar, "cell 0 2, grow");
        
        SwingHelper.removeOpaqueness(updateCheck);
        updateCheck.setForeground(Color.WHITE);
    }

    private void updateInstanceList() {
        SwingUtilities.invokeLater(() -> {
            DefaultComboBoxModel<Instance> model = (DefaultComboBoxModel<Instance>) instanceSelector.getModel();
            Object selected = instanceSelector.getSelectedItem();
            
            model.removeAllElements();
            for (Instance instance : launcher.getInstances().getInstances()) {
                model.addElement(instance);
            }
            
            if (model.getSize() > 0) {
                // Restore selection if possible, otherwise default to first
                if (selected != null && model.getIndexOf(selected) != -1) {
                    instanceSelector.setSelectedItem(selected);
                } else {
                    instanceSelector.setSelectedIndex(0);
                }
            }
        });
    }

    private JPanel createAccountPanel() {
        JPanel panel = new JPanel(new MigLayout("insets 5, fill", "[right][40!]", "[]")) {
            @Override
            protected void paintComponent(Graphics g) {
                if (getBackground() != null && isOpaque()) {
                    g.setColor(getBackground());
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                }
                super.paintComponent(g);
            }
        };
        panel.setOpaque(false); // We handle painting
        panel.setBackground(new Color(0,0,0,0));
        panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
        nameLabel.setForeground(Color.WHITE);

        panel.add(nameLabel);
        panel.add(headLabel);

        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                AccountSelectDialog.showAccountRequest(FancyLauncherFrame.this, launcher);
                updateAccountInfo();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                panel.setOpaque(true);
                panel.setBackground(HOVER_COLOR);
                panel.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                panel.setOpaque(false);
                panel.setBackground(new Color(0,0,0,0));
                panel.repaint();
            }
        });

        return panel;
    }

    private void updateAccountInfo() {
        AccountList accounts = launcher.getAccounts();
        String username = "Guest";
        Icon headIcon = SwingHelper.createIcon(Launcher.class, "default_skin.png", 32, 32);

        if (accounts.getSize() > 0) {
            SavedSession session = accounts.getElementAt(0);
            username = session.getUsername();
            if (session.getAvatarImage() != null) {
                ImageIcon raw = new ImageIcon(session.getAvatarImage());
                headIcon = new ImageIcon(getCircularImage(raw.getImage(), 32, 32));
            }
        }

        nameLabel.setText("Welcome, " + username);
        headLabel.setIcon(headIcon);
    }

    private Image getCircularImage(Image img, int w, int h) {
        BufferedImage avatar = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = avatar.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setClip(new Ellipse2D.Float(0, 0, w, h));
        g2.drawImage(img, 0, 0, w, h, null);
        g2.dispose();
        return avatar;
    }

    @Override
    protected JPanel createContainerPanel() {
        this.container = new FancyBackgroundPanel();
        return this.container;
    }

    @Override
    protected WebpagePanel createNewsPanel() {
        WebpagePanel panel = super.createNewsPanel();
        panel.setBrowserBorder(new EmptyBorder(0, 0, 0, 0));
        return panel;
    }

    private void updateLaunchButton() {
        Instance instance = (Instance) instanceSelector.getSelectedItem();
        if (instance != null) {
            if (!instance.isInstalled()) {
                launchButton.setText("Install");
                // Blue for Install
                launchButton.setBackground(new Color(0, 120, 215));
                launchButton.setForeground(Color.WHITE);
            } else if (instance.isUpdatePending()) {
                launchButton.setText("Update");
                // Orange for Update
                launchButton.setBackground(new Color(255, 140, 0));
                launchButton.setForeground(Color.WHITE);
            } else {
                launchButton.setText("Play");
                // Green for Play
                launchButton.setBackground(new Color(34, 139, 34));
                launchButton.setForeground(Color.WHITE);
            }
        }
    }

    private class InstanceComboRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Instance) {
                Instance instance = (Instance) value;
                setText(instance.getTitle());
                
                if (instance.isLocal() && instance.isInstalled()) {
                    setIcon(instanceIcon);
                    if (instance.isUpdatePending()) {
                        setText(instance.getTitle() + " (Update Available)");
                    }
                } else {
                    setIcon(downloadIcon);
                    setText(instance.getTitle() + " (Install)");
                }
            }
            return this;
        }
    }

    private static class GlassPanel extends JPanel {
        public GlassPanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(GLASS_COLOR);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }
}