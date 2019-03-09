package de.browniecodez.tool;

import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.swing.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class Main extends JFrame {
    private static Pattern USER_ID = Pattern.compile("user_id=([^&]+)");
    private static Pattern RESOURCE_ID = Pattern.compile("resource_id=([^&]+)");
    private static Pattern NONCEID_PATTERN = Pattern.compile("[0-9]{7}");
    private static List<String> possibleNonces = new ArrayList<String>() {
    };
    private static ArrayList<String> userIds = new ArrayList<>();
    private static ArrayList<String> resourceIds = new ArrayList<>();
    private static JTextField textField;

    public static void main(String[] var0) {
        createGUI();
    }

    private static void createGUI() {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                //e.printStackTrace();
            }

            Main finder = new Main();
            finder.setTitle("SpigotMC Plugin Info Tool by BrownieCodez");
            finder.setResizable(false);
            finder.setSize(600, 100);
            finder.setLocationRelativeTo(null);
            finder.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            finder.getContentPane().setLayout(new FlowLayout());
            JLabel label = new JLabel("Select jar file:");
            textField = new JTextField();
            textField.setEditable(false);
            textField.setColumns(18);
            JButton selectButton = new JButton("Select");
            selectButton.setToolTipText("Select jar file");
            selectButton.addActionListener((e) -> {
                JFileChooser chooser = new JFileChooser();
                if (textField.getText() != null && !textField.getText().isEmpty()) {
                    chooser.setSelectedFile(new File(textField.getText()));
                }
                chooser.setMultiSelectionEnabled(false);
                chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                int result = chooser.showOpenDialog(finder);
                if (result == 0) {
                    SwingUtilities.invokeLater(() ->
                            textField.setText(chooser.getSelectedFile().getAbsolutePath())
                    );
                }
            });
            JButton getInfoButton = new JButton("Get Info");
            getInfoButton.addActionListener((e) -> {
                if (textField.getText() == null || textField.getText().isEmpty()) {
                    JOptionPane.showMessageDialog(null, "You must select a valid jar file!",
                            "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                getInfoButton.setText("Processing...");
                getInfoButton.setEnabled(false);
                SwingUtilities.invokeLater(() -> {
                    try {
                        if (!textField.getText().endsWith(".jar")) {
                            throw new IllegalArgumentException("File must be a jar.");
                        }

                        File input = new File(textField.getText());

                        if (!input.exists()) {
                            throw new FileNotFoundException("The file " + input.getName() + " doesn't exist.");
                        }

                        process(input);
                        JOptionPane.showMessageDialog(null, returnInfo(),
                                "Done", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Throwable t) {
                        JOptionPane.showMessageDialog(null, t,
                                "Error", JOptionPane.ERROR_MESSAGE);
                        t.printStackTrace();
                    } finally {
                        getInfoButton.setText("Get Informations");
                        getInfoButton.setEnabled(true);
                        userIds.clear();
                        resourceIds.clear();
                    }
                });
            });
            JPanel panel = new JPanel(new FlowLayout());
            panel.add(label);
            panel.add(textField);
            panel.add(selectButton);
            JPanel panel2 = new JPanel(new FlowLayout());
            panel2.add(getInfoButton);
            JPanel panel3 = new JPanel(new BorderLayout());
            panel3.add(panel, "North");
            panel3.add(panel2, "South");
            finder.getContentPane().add(panel3);
            finder.setVisible(true);
        });
    }

    private static void process(File input) throws Throwable {
        ZipFile zipFile = new ZipFile(input);
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        Map<String, ClassNode> classes = new HashMap<>();

        try {
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();

                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    try (InputStream in = zipFile.getInputStream(entry)) {
                        ClassReader cr = new ClassReader(in);
                        ClassNode classNode = new ClassNode();
                        cr.accept(classNode, 0);
                        classes.put(classNode.name, classNode);

                        if (in != null) {
                            in.close();
                        }
                    } catch (Throwable t) {
                        //t.printStackTrace();;
                    }
                }
            }
        } finally {
            zipFile.close();
        }

        classes.values().forEach(classNode -> {
            findUserId(classNode);
            findResourceId(classNode);
        });
    }
    
    private static void findUserId(ClassNode classNode) {
        classNode.methods.stream().filter(methodNode -> methodNode.name.equals("loadConfig0")
                && methodNode.desc.equals("()V")).forEach(methodNode -> {
            for (AbstractInsnNode insn : methodNode.instructions.toArray()) {
                if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof String) {
                    String str = (String) ((LdcInsnNode) insn).cst;
                    if (str.contains("spigotmc.org")) {
                        Matcher matcher = USER_ID.matcher(str);
                        if (!matcher.find()) {
                            throw new IllegalStateException("Could not find Spigot User ID in " + classNode.name + '.'
                                    + methodNode.name + ' ' + methodNode.desc);
                        }

                        String result = matcher.group(1);
                        if (!userIds.contains(result)) {
                            userIds.add(result);
                        }
                    }
                }
            }
        });
    }

    private static void findResourceId(ClassNode classNode) {
        classNode.methods.stream().filter(methodNode -> methodNode.name.equals("loadConfig0")
                && methodNode.desc.equals("()V")).forEach(methodNode -> {
            for (AbstractInsnNode insn : methodNode.instructions.toArray()) {
                if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof String) {
                    String str = (String) ((LdcInsnNode) insn).cst;
                    if (str.contains("spigotmc.org")) {
                        Matcher matcher = RESOURCE_ID.matcher(str);
                        if (!matcher.find()) {
                            throw new IllegalStateException("Could not find Spigot Resource ID in " + classNode.name + '.'
                                    + methodNode.name + ' ' + methodNode.desc);
                        }

                        String result = matcher.group(1);
                        if (!resourceIds.contains(result)) {
                            resourceIds.add(result);
                        }
                        
                    }
                }
            }
        });
    }
	public static String onId(String paramString) {
		try {
			URL localURL = new URL("https://www.spigotmc.org/members/" + paramString);
			URLConnection localURLConnection = localURL.openConnection();
			localURLConnection.setRequestProperty("User-Agent", "Mozilla/5.0");

			BufferedReader localBufferedReader = new BufferedReader(
					new InputStreamReader(localURLConnection.getInputStream()));

			String str1 = "";
			String str2 = "";
			while ((str2 = localBufferedReader.readLine()) != null) {
				str1 = str1 + str2;
			}
			return str1.split("<title>")[1].split("</title>")[0].split(" | ")[0];
		} catch (IOException localIOException) {
		}
		return null;
	}

    private static String returnInfo() {
        StringBuilder sb = new StringBuilder();
        
        String id = userIds.toString().replace("]", "").replace("[", "");
        sb.append("\nSpigot Username: " + onId(id));
        
        sb.append("\nSpigot UserID: ");
        userIds.forEach(sb::append);

        return new String(sb);
    }
}
