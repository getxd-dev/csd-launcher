/*
 * SKCraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.skin;

import org.pushingpixels.substance.api.shaper.StandardButtonShaper;

import javax.swing.*;
import java.awt.*;

public class LauncherButtonShaper extends StandardButtonShaper {

    public Dimension getPreferredSize(AbstractButton button, Dimension uiPreferredSize) {
        Dimension size = super.getPreferredSize(button, uiPreferredSize);
        return new Dimension(size.width + 10, size.height + 6);
    }

}
