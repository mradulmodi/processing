/* -*- mode: jde; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  PdeBase - base class for the main processing application
  Part of the Processing project - http://Proce55ing.net

  Copyright (c) 2001-03 
  Ben Fry, Massachusetts Institute of Technology and 
  Casey Reas, Interaction Design Institute Ivrea

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License 
  along with this program; if not, write to the Free Software Foundation, 
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.*;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.undo.*;

#ifdef MACOS
import com.apple.mrj.*;
#endif


/**
 * Primary role of this class is for platform identification and
 * general interaction with the system (launching URLs, loading 
 * files and images, etc) that comes from that.
 */
public class PdeBase extends JFrame implements ActionListener
#ifdef MACOS
             , MRJAboutHandler
             , MRJQuitHandler
             , MRJPrefsHandler
#endif
{
  static final String VERSION = "0068 Alpha";

  //static Properties properties;
  PdePreferences preferences;
  static Properties keywords; // keyword -> reference html lookup

  //static Frame frame;  // now 'this'
  //static String encoding;
  static Image icon;

  // indicator that this is the first time this feller has used p5
  //static boolean firstTime;

  //boolean errorState;
  PdeEditor editor;

  //WindowAdapter windowListener;

  //Menu serialMenu;
  JMenuItem saveMenuItem;
  JMenuItem saveAsMenuItem;
  JMenuItem beautifyMenuItem;
  //CheckboxMenuItem externalEditorItem;

  //Menu renderMenu;
  //CheckboxMenuItem normalItem, openglItem;
  //MenuItem illustratorItem;

  // the platforms
  static final int WINDOWS = 1;
  static final int MACOS9  = 2;
  static final int MACOSX  = 3;
  static final int LINUX   = 4;
  static final int IRIX    = 5;
  static int platform;

  static final String platforms[] = {
    "", "windows", "macos9", "macosx", "linux", "irix"
  };


  static public void main(String args[]) {
    //System.getProperties().list(System.out);
    PdeBase app = new PdeBase();
  }


  // hack for #@#)$(* macosx
  public Dimension getMinimumSize() {
    return new Dimension(500, 500);
  }


  public PdeBase() {
    super(WINDOW_TITLE);


    // figure out which operating system

    if (System.getProperty("mrj.version") != null) {  // running on a mac
      platform = (System.getProperty("os.name").equals("Mac OS X")) ?
        MACOSX : MACOS9;

    } else {
      String osname = System.getProperty("os.name");

      if (osname.indexOf("Windows") != -1) {
        platform = WINDOWS;

      } else if (osname.equals("Linux")) {  // true for the ibm vm
        platform = LINUX;

      } else if (osname.equals("Irix")) {
        platform = IRIX;

      } else {
        platform = WINDOWS;  // probably safest
        System.out.println("unhandled osname: \"" + osname + "\"");
      }
    }


    // set the look and feel before opening the window

    try {
      if (platform == LINUX) {
        // linux is by default (motif?) even uglier than metal
        // actually, i'm using native menus, so they're ugly and
        // motif-looking. ick. need to fix this.
        UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
      } else {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      }
    } catch (Exception e) { 
      e.printStackTrace();
    }


    // set the window icon

    try {
      icon = Toolkit.getDefaultToolkit().getImage("lib/icon.gif");
      this.setIconImage(icon);
    } catch (Exception e) { } // fail silently, no big whup


    // add listener to handle window close box hit event

    addWindowListener(new WindowAdapter() {
        public void windowClosing(WindowEvent e) {
          handleQuit();
        }
      });
    //frame.addWindowListener(windowListener);
    //this.addWindowListener(windowListener);


    // load in preferences (last sketch used, window placement, etc)

    preferences = new PdePreferences();


    // read in the keywords for the reference

    final String KEYWORDS = "pde_keywords.properties";
    keywords = new Properties();
    try {
      if ((PdeBase.platform == PdeBase.MACOSX) || 
          (PdeBase.platform == PdeBase.MACOS9)) {
        // macos doesn't seem to think that files in the lib folder
        // are part of the resources, unlike windows or linux.
        // actually, this is only the case when running as a .app, 
        // since it works fine from run.sh, but not Processing.app
        keywords.load(new FileInputStream("lib/" + KEYWORDS));

      } else {  // other, more reasonable operating systems
        keywords.load(getClass().getResource(KEYWORDS).openStream());
      }

    } catch (Exception e) {
      String message = 
        "An error occurred while loading the keywords,\n" + 
        "\"Find in reference\" will not be available.";
      JOptionPane.showMessageDialog(this, message, 
                                    "Problem loading keywords",
                                    JOptionPane.WARNING_MESSAGE);

      System.err.println(e.toString());
      e.printStackTrace();
    }


    // build the editor object

    //editor = new PdeEditor(this);
    //getContentPane().setLayout(new BorderLayout());
    //getContentPane().add("Center", editor);


#ifdef MACOS
      // #@$*(@#$ apple.. always gotta think different
      MRJApplicationUtils.registerAboutHandler(this);
      MRJApplicationUtils.registerPrefsHandler(this);
      MRJApplicationUtils.registerQuitHandler(this);
#endif

    // load preferences and finish up

    // handle layout
    this.pack();  // maybe this should be before the setBounds call
    // do window placement before loading sketch stuff
    restorePreferences();

    // now that everything is set up, open last-used sketch, etc.
    editor.restorePreferences();

    show();
  }


  public void restorePreferences() {
    // figure out window placement

    Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
    boolean windowPositionInvalid = false;

    if (PdePreferences.get("last.screen.height") != null) {
      // if screen size has changed, the window coordinates no longer
      // make sense, so don't use them unless they're identical
      int screenW = getInteger("last.screen.width");
      int screenH = getInteger("last.screen.height");

      if ((screen.width != screenW) || (screen.height != screenH)) {
        windowPositionInvalid = true;
      }
    } else {
      windowPositionInvalid = true;
    }

    if (windowPositionInvalid) {
      int windowH = PdePreferences.getInteger("default.window.height");
      int windowW = PdePreferences.getInteger("default.window.width");
      setBounds((screen.width - windowW) / 2, 
                (screen.height - windowH) / 2,
                windowW, windowH);
      // this will be invalid as well, so grab the new value
      PdePreferences.setInteger("last.divider.location", 
                                splitPane.getDividerLocation());
    } else {
      setBounds(PdePreferences.getInteger("last.window.x"), 
                PdePreferences.getInteger("last.window.y"), 
                PdePreferences.getInteger("last.window.width"), 
                PdePreferences.getInteger("last.window.height"));
    }

    applyPreferences();
  }


  public void applyPreferences() {
    rebuildSketchbookMenu(sketchbookMenu);
  }


  public void storePreferences() {
    Rectangle bounds = getBounds();
    PdePreferences.setInteger("last.window.x", bounds.x);
    PdePreferences.setInteger("last.window.y", bounds.y);
    PdePreferences.setInteger("last.window.width", bounds.width);
    PdePreferences.setInteger("last.window.height", bounds.height);
  }


  // listener for sketchbk items uses getParent() to figure out
  // the directories above it

  class SketchbookMenuListener implements ActionListener {
    String path;

    public SketchbookMenuListener(String path) {
      this.path = path;
    }

    public void actionPerformed(ActionEvent e) {
      String name = e.getActionCommand();
      editor.skOpen(path + File.separator + name, name);
    }
  }

  public void rebuildSketchbookMenu() {
    rebuildSketchbookMenu(sketchbookMenu);
  }

  public void rebuildSketchbookMenu(Menu menu) {
    menu.removeAll();

    try {
      //MenuItem newSketchItem = new MenuItem("New Sketch");
      //newSketchItem.addActionListener(this);
      //menu.add(newSkechItem);
      //menu.addSeparator();

      sketchbookFolder = 
        new File(PdePreferences.get("sketchbook.path", "sketchbook"));
      sketchbookPath = sketchbookFolder.getAbsolutePath();
      if (!sketchbookFolder.exists()) {
        System.err.println("sketchbook folder doesn't exist, " + 
                           "making a new one");
        sketchbookFolder.mkdirs();
      }

      // files for the current user (for now, most likely 'default')

      // header knows what the current user is
      String userPath = sketchbookPath + 
        File.separator + editor.userName;

      File userFolder = new File(userPath);
      if (!userFolder.exists()) {
        System.err.println("sketchbook folder for '" + editor.userName + 
                           "' doesn't exist, creating a new one");
        userFolder.mkdirs();
      }

      /*
      SketchbookMenuListener userMenuListener = 
        new SketchbookMenuListener(userPath);

      String entries[] = new File(userPath).list();
      boolean added = false;
      for (int j = 0; j < entries.length; j++) {
        if (entries[j].equals(".") || 
            entries[j].equals("..") ||
            entries[j].equals("CVS")) continue;
        //entries[j].equals(".cvsignore")) continue;
        added = true;
        if (new File(userPath, entries[j] + File.separator + 
                     entries[j] + ".pde").exists()) {
          MenuItem item = new MenuItem(entries[j]);
          item.addActionListener(userMenuListener);
          menu.add(item);
        }
        //submenu.add(entries[j]);
      }
      if (!added) {
        MenuItem item = new MenuItem("No sketches");
        item.setEnabled(false);
        menu.add(item);
      }
      menu.addSeparator();
      */
      if (addSketches(menu, userFolder, false)) {
        menu.addSeparator();
      }
      if (!addSketches(menu, sketchbookFolder, true)) {
        MenuItem item = new MenuItem("No sketches");
        item.setEnabled(false);
        menu.add(item);
      }

      /*
      // doesn't seem that refresh is worthy of its own menu item
      // people can stop and restart p5 if they want to muck with it
      menu.addSeparator();
      MenuItem item = new MenuItem("Refresh");
      item.addActionListener(this);
      menu.add(item);
      */

    } catch (IOException e) {
      e.printStackTrace();
    }
  }


  protected boolean addSketches(Menu menu, File folder, 
                                /*boolean allowUser,*/ boolean root) 
    throws IOException {
    // skip .DS_Store files, etc
    if (!folder.isDirectory()) return false;

    String list[] = folder.list();
    SketchbookMenuListener listener = 
      new SketchbookMenuListener(folder.getAbsolutePath());

    boolean ifound = false;

    for (int i = 0; i < list.length; i++) {
      if (list[i].equals(editor.userName) && root) continue;

      if (list[i].equals(".") ||
          list[i].equals("..") ||
          list[i].equals("CVS")) continue;

      File subfolder = new File(folder, list[i]);
      if (new File(subfolder, list[i] + ".pde").exists()) {
        MenuItem item = new MenuItem(list[i]);
        item.addActionListener(listener);
        menu.add(item);
        ifound = true;

      } else {  // might contain other dirs, get recursive
        Menu submenu = new Menu(list[i]);
        // needs to be separate var 
        // otherwise would set ifound to false
        boolean found = addSketches(submenu, subfolder, false);
        if (found) {
          menu.add(submenu);
          ifound = true;
        }
      }
    }
    return ifound;
  }


  // interfaces for MRJ Handlers, but naming is fine 
  // so used internally for everything else

  public void handleAbout() {
    //System.out.println("the about box will now be shown");
    final Image image = getImage("about.jpg", this);
    int w = image.getWidth(this);
    int h = image.getHeight(this);
    final Window window = new Window(this) {
        public void paint(Graphics g) {
          g.drawImage(image, 0, 0, null);

          /*
            // does nothing..
          Graphics2D g2 = (Graphics2D) g;
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, 
                              RenderingHints.VALUE_ANTIALIAS_OFF);
          */

          g.setFont(new Font("SansSerif", Font.PLAIN, 11));
          g.setColor(Color.white);
          g.drawString(VERSION, 50, 30);
        }
      };
    window.addMouseListener(new MouseAdapter() {
        public void mousePressed(MouseEvent e) {
          window.dispose();
        }
      });
    Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
    window.setBounds((screen.width-w)/2, (screen.height-h)/2, w, h);
    window.show();
  }


  /**
   * Show the (already created on app init) preferences window.
   */
  public void handlePrefs() {
    // make sure this blocks until finished
    preferences.showFrame();

    // may need to rebuild sketch and other menus
    applyPreferences();

    // next have editor do its thing
    editor.appyPreferences();
  }


  /** 
   * Quit, but first ask user if it's ok. Also store preferences
   * to disk just in case they want to quit. Final exit() happens 
   * in PdeEditor since it has the callback from PdeEditorStatus.
   */
  public void handleQuit() {
    storePreferences();
    editor.storePreferences();

    // this will save the prefs even if quit is cancelled, but who cares
    PdePreferences.save();

    // check to see if the person actually wants to quit
    editor.doQuit();
  }


  /**
   * Handle menu selections.
   */
  public void actionPerformed(ActionEvent event) {
    String command = event.getActionCommand();
    //System.out.println(command);

    if (command.equals("New")) {
      editor.skNew();
      //editor.initiate(Editor.NEW);

    } else if (command.equals("Save")) {
      editor.doSave();

    } else if (command.equals("Save as...")) {
      editor.skSaveAs(false);

    } else if (command.equals("Rename...")) {
      editor.skSaveAs(true);

    } else if (command.equals("Export to Web")) {
      editor.skExport();

    } else if (command.equals("Preferences")) {
      handlePrefs();

    } else if (command.equals("Quit")) {
      handleQuit();

    } else if (command.equals("Run")) {
      editor.doRun(false);

    } else if (command.equals("Present")) {
      editor.doRun(true);

    } else if (command.equals("Stop")) {    
      if (editor.presenting) {
        editor.doClose();
      } else {
        editor.doStop();
      }
    } else if (command.equals("Beautify")) {
      editor.doBeautify();

    } else if (command.equals("Add file...")) {
      editor.addFile();

    } else if (command.equals("Create font...")) {
      new PdeFontBuilder(new File(editor.sketchDir, "data"));

    } else if (command.equals("Show sketch folder")) {
      openFolder(editor.sketchDir);

    } else if (command.equals("Help")) {
      openURL(System.getProperty("user.dir") + 
              File.separator + "reference" + 
              File.separator + "environment" +
              File.separator + "index.html");

    } else if (command.equals("Proce55ing.net")) {
      openURL("http://Proce55ing.net/");

    } else if (command.equals("Reference")) {
      openURL(System.getProperty("user.dir") + File.separator + 
              "reference" + File.separator + "index.html");

    } else if (command.equals("About Processing")) {
      handleAbout();
    }
  }


  /**
   * Given the reference filename from the keywords list, 
   * builds a URL and passes it to openURL.
   */
  static public void showReference(String referenceFile) {
    String currentDir = System.getProperty("user.dir");
    openURL(currentDir + File.separator + 
            "reference" + File.separator + 
            referenceFile + ".html");
  }


  /**
   * Implements the cross-platform headache of opening URLs
   */
  static public void openURL(String url) { 
    //System.out.println("opening url " + url);
    try {
      if (platform == WINDOWS) {
        // this is not guaranteed to work, because who knows if the 
        // path will always be c:\progra~1 et al. also if the user has
        // a different browser set as their default (which would 
        // include me) it'd be annoying to be dropped into ie.
        //Runtime.getRuntime().exec("c:\\progra~1\\intern~1\\iexplore " 
        // + currentDir 

	// the following uses a shell execute to launch the .html file
        // note that under cygwin, the .html files have to be chmodded +x
        // after they're unpacked from the zip file. i don't know why,
        // and don't understand what this does in terms of windows 
        // permissions. without the chmod, the command prompt says 
        // "Access is denied" in both cygwin and the "dos" prompt.
        //Runtime.getRuntime().exec("cmd /c " + currentDir + "\\reference\\" + 
        //                    referenceFile + ".html");
        if (url.startsWith("http://")) {
          // open dos prompt, give it 'start' command, which will
          // open the url properly. start by itself won't work since
          // it appears to need cmd
          Runtime.getRuntime().exec("cmd /c start " + url);
        } else {
          // just launching the .html file via the shell works
          // but make sure to chmod +x the .html files first
          // also place quotes around it in case there's a space
          // in the user.dir part of the url
          Runtime.getRuntime().exec("cmd /c \"" + url + "\"");
        }

#ifdef MACOS
      } else if (platform == MACOSX) {
        //com.apple.eio.FileManager.openURL(url);

        if (!url.startsWith("http://")) {
          // prepend file:// on this guy since it's a file
          url = "file://" + url;

          // replace spaces with %20 for the file url
          // otherwise the mac doesn't like to open it
          // can't just use URLEncoder, since that makes slashes into
          // %2F characters, which is no good. some might say "useless"
          if (url.indexOf(' ') != -1) {
            StringBuffer sb = new StringBuffer();
            char c[] = url.toCharArray();
            for (int i = 0; i < c.length; i++) {
              if (c[i] == ' ') {
                sb.append("%20");
              } else {
                sb.append(c[i]);
              }
            }
            url = sb.toString();
          }
        }
        //System.out.println("trying to open " + url);
        com.apple.mrj.MRJFileUtils.openURL(url);

      } else if (platform == MACOS9) {
        com.apple.mrj.MRJFileUtils.openURL(url);
#endif

      } else if (platform == LINUX) {
        // how's mozilla sound to ya, laddie?
        Runtime.getRuntime().exec(new String[] { "mozilla", url });

      } else {
        System.err.println("unspecified platform");
      }

    } catch (IOException e) {
      e.printStackTrace();
    }
  }


  /** 
   * Implements the other cross-platform headache of opening
   * a folder in the machine's native file browser.
   */
  static public void openFolder(File file) {
    try {
      String folder = file.getAbsolutePath();

      if (platform == WINDOWS) {
        // doesn't work
        //Runtime.getRuntime().exec("cmd /c \"" + folder + "\"");

        // works fine on winxp, prolly win2k as well
        Runtime.getRuntime().exec("explorer \"" + folder + "\"");

        // not tested
        //Runtime.getRuntime().exec("start explorer \"" + folder + "\"");

#ifdef MACOS
      } else if (platform == MACOSX) {
        openURL(folder);  // handles char replacement, etc

#endif
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  //

  // could also do showMessage with JOptionPane.INFORMATION_MESSAGE

  //

  static public void showWarning(String title, String message, 
                                 Exception e) {
    if (title == null) title = "Warning";
    JOptionPane.showMessageDialog(this, message, title,
                                  JOptionPane.WARNING_MESSAGE);

    //System.err.println(e.toString());
    if (e != null) e.printStackTrace();
  }

  //

  static public void showError(String title, String message, 
                               Exception e) {
    if (title == null) title = "Error";
    JOptionPane.showMessageDialog(this, message, title,
                                  JOptionPane.ERROR_MESSAGE);

    if (e != null) e.printStackTrace();
  }

  //

  // used by PdeEditorButtons, but probably more later
  static public Image getImage(String name, Component who) {
    Image image = null;
    //if (isApplet()) {
    //image = applet.getImage(applet.getCodeBase(), name);
    //} else {
    Toolkit tk = Toolkit.getDefaultToolkit();

    if (PdeBase.platform == PdeBase.MACOSX) {
      //String pkg = "Proce55ing.app/Contents/Resources/Java/";
      //image = tk.getImage(pkg + name);
      image = tk.getImage("lib/" + name);
    } else if (PdeBase.platform == PdeBase.MACOS9) {
      image = tk.getImage("lib/" + name);
    } else {
      image = tk.getImage(who.getClass().getResource(name));
    }

    //image =  tk.getImage("lib/" + name);
    //URL url = PdeApplet.class.getResource(name);
    //image = tk.getImage(url);
    //}
    //MediaTracker tracker = new MediaTracker(applet);
    MediaTracker tracker = new MediaTracker(who); //frame);
    tracker.addImage(image, 0);
    try {
      tracker.waitForAll();
    } catch (InterruptedException e) { }      
    return image;
  }
}
