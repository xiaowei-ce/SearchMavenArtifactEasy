package cc.xiaowei;

import cc.xiaowei.service.MavenSearchService;
import cc.xiaowei.service.MavenSearchService.ArtifactEntry;
import cc.xiaowei.utils.StringUtils;
import cc.xiaowei.service.PomXmlManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class MavenSearchDialog extends DialogWrapper {

    private static final Logger LOG = Logger.getInstance(MavenSearchDialog.class);

    static final String DIALOG_TITLE = "Maven Artifact Search";
    static final String NO_VERSION_ITEM = "[No Version / Omit]";
    static final String EMPTY_TEXT_NO_RESULTS = "No artifacts found";
    static final String LOADING_TEXT = "Loading...";
    static final String SEARCH_PLACEHOLDER = "Search Maven artifacts (e.g. lombok, gson)...";

    private final Project project;

    private JBTextField searchField;
    private JButton searchBtn;
    private JButton clearBtn;
    private JBTable resultTable;
    private DefaultTableModel tableModel;
    private ComboBox<String> versionCombo;
    private JTextArea xmlPreview;
    private JButton copyBtn;
    private JButton addToPomBtn;

    private String selectedGroupId;
    private String selectedArtifactId;
    private boolean isSearching = false;

    // Pagination state
    private int currentPage = 0;
    private int totalPages = 0;
    private int totalResults = 0;
    private String currentQuery = "";
    private JButton prevPageBtn;
    private JButton nextPageBtn;
    private JBTextField pageNumField;
    private JLabel pageTotalLabel;

    public MavenSearchDialog(Project project) {
        super(project);
        this.project = project;
        LOG.info("MavenSearchDialog opened, project=" + (project != null ? project.getName() : "null"));
        setTitle(DIALOG_TITLE);
        setResizable(true);
        setSize(800, 600);
        init();
        checkPomFileStatus();
    }

    private void checkPomFileStatus() {
        addToPomBtn.setEnabled(false);
        if (project == null) return;
        FileEditorManager fem = FileEditorManager.getInstance(project);
        VirtualFile[] selectedFiles = fem.getSelectedFiles();
        if (selectedFiles.length > 0 && "pom.xml".equals(selectedFiles[0].getName())) {
            addToPomBtn.setEnabled(true);
            LOG.info("pom.xml detected in active editor, Add to pom.xml enabled");
        } else {
            LOG.info("No pom.xml in active editor (file="
                    + (selectedFiles.length > 0 ? selectedFiles[0].getName() : "none")
                    + "), Add to pom.xml disabled");
        }
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(JBUI.scale(8), JBUI.scale(8)));
        panel.setBorder(JBUI.Borders.empty(10));

        // ===== North: Search bar =====
        JPanel northPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        searchField = new JBTextField();
        searchField.getEmptyText().setText(SEARCH_PLACEHOLDER);
        searchField.setPreferredSize(new Dimension(JBUI.scale(400), JBUI.scale(30)));
        searchBtn = new JButton("Search");
        clearBtn = new JButton("Clear");
        clearBtn.setEnabled(false);
        northPanel.add(searchField);
        northPanel.add(searchBtn);
        northPanel.add(clearBtn);
        panel.add(northPanel, BorderLayout.NORTH);

        // ===== Center: Result table + Pagination =====
        tableModel = new DefaultTableModel(new String[]{"Group ID", "Artifact ID", "Latest Version"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        resultTable = new JBTable(tableModel);
        resultTable.setRowSelectionAllowed(true);
        resultTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultTable.getTableHeader().setReorderingAllowed(false);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        resultTable.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);

        // Pagination bar with page input
        JPanel pagingPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        prevPageBtn = new JButton("< Prev");
        prevPageBtn.setEnabled(false);

        JPanel pageInputGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        pageInputGroup.add(new JLabel("Page"));
        pageNumField = new JBTextField("0");
        pageNumField.setEnabled(false);
        pageNumField.setPreferredSize(new Dimension(JBUI.scale(55), JBUI.scale(28)));
        pageNumField.setHorizontalAlignment(JTextField.CENTER);
        pageInputGroup.add(pageNumField);
        pageTotalLabel = new JLabel("/ 0  (0 results)");
        pageInputGroup.add(pageTotalLabel);

        nextPageBtn = new JButton("Next >");
        nextPageBtn.setEnabled(false);
        pagingPanel.add(prevPageBtn);
        pagingPanel.add(pageInputGroup);
        pagingPanel.add(nextPageBtn);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(new JBScrollPane(resultTable), BorderLayout.CENTER);
        centerPanel.add(pagingPanel, BorderLayout.SOUTH);
        panel.add(centerPanel, BorderLayout.CENTER);

        // ===== South: Version + Preview + Actions =====
        JPanel southPanel = new JPanel();
        southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));

        // Version row
        JPanel versionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        versionPanel.add(new JLabel("Version:"));
        versionCombo = new ComboBox<>();
        versionCombo.setPreferredSize(new Dimension(JBUI.scale(300), JBUI.scale(28)));
        versionCombo.addItem(NO_VERSION_ITEM);
        versionCombo.setEnabled(false);
        versionPanel.add(versionCombo);
        southPanel.add(versionPanel);

        // Preview row
        xmlPreview = new JTextArea(4, 60);
        xmlPreview.setEditable(false);
        xmlPreview.setBackground(JBColor.background());
        xmlPreview.setForeground(new JBColor(0x333333, 0xA5C25C));
        xmlPreview.setBorder(JBUI.Borders.empty(4));
        JPanel previewWrapper = new JPanel(new BorderLayout());
        previewWrapper.setBorder(BorderFactory.createTitledBorder("XML Preview"));
        previewWrapper.add(new JBScrollPane(xmlPreview), BorderLayout.CENTER);
        southPanel.add(previewWrapper);

        // Action row
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 4));
        copyBtn = new JButton("Copy XML");
        copyBtn.setEnabled(false);
        addToPomBtn = new JButton("Add to pom.xml");
        actionPanel.add(copyBtn);
        actionPanel.add(addToPomBtn);
        southPanel.add(actionPanel);

        panel.add(southPanel, BorderLayout.SOUTH);

        // ===== Event Listeners =====
        setupListeners();

        return panel;
    }

    private void setupListeners() {
        // Search button click
        searchBtn.addActionListener(e -> performSearch());

        // Clear button
        clearBtn.addActionListener(e -> performClear());

        // Enter key in search field
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    performSearch();
                }
            }
        });

        // Table row selection → fetch versions
        resultTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int selectedRow = resultTable.getSelectedRow();
            if (selectedRow >= 0 && selectedRow < tableModel.getRowCount()) {
                String groupId = (String) tableModel.getValueAt(selectedRow, 0);
                String artifactId = (String) tableModel.getValueAt(selectedRow, 1);
                if (EMPTY_TEXT_NO_RESULTS.equals(groupId)) return;
                selectedGroupId = groupId;
                selectedArtifactId = artifactId;
                fetchVersions(groupId, artifactId);
            }
        });

        // Version combo change → update preview
        versionCombo.addActionListener(e -> updateXmlPreview());

        // Copy button
        copyBtn.addActionListener(e -> {
            String text = xmlPreview.getText();
            if (text != null && !text.isEmpty()) {
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(new StringSelection(text), null);
            }
        });

        // Add to pom.xml button
        addToPomBtn.addActionListener(e -> addDependencyToPom());

        // Pagination buttons
        prevPageBtn.addActionListener(e -> goToPage(currentPage - 1));
        nextPageBtn.addActionListener(e -> goToPage(currentPage + 1));

        // Enter key in page number field → jump to page
        pageNumField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    jumpToPage();
                }
            }
        });
    }

    // ========================================================================
    //  Feature A: Search with Pagination
    // ========================================================================
    private void performClear() {
        if (isSearching) return;
        searchField.setText("");
        currentQuery = "";
        currentPage = 0;
        totalPages = 0;
        totalResults = 0;
        tableModel.setRowCount(0);
        resultTable.getEmptyText().setText("");
        clearBtn.setEnabled(false);
        prevPageBtn.setEnabled(false);
        nextPageBtn.setEnabled(false);
        pageNumField.setText("0");
        pageNumField.setEnabled(false);
        pageTotalLabel.setText("/ 0  (0 results)");
        clearVersionAndPreview();
    }

    private void performSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) return;
        currentQuery = query;
        currentPage = 0;
        totalPages = 0;
        totalResults = 0;
        doSearch(0);
    }

    private void goToPage(int page) {
        if (page < 0 || page >= totalPages) return;
        if (currentQuery.isEmpty()) return;
        currentPage = page;
        doSearch(page);
    }

    private void doSearch(int page) {
        if (isSearching) return;

        isSearching = true;
        searchBtn.setEnabled(false);
        prevPageBtn.setEnabled(false);
        nextPageBtn.setEnabled(false);

        // Show loading indicator
        tableModel.setRowCount(0);
        resultTable.getEmptyText().setText(LOADING_TEXT);
        selectedGroupId = null;
        selectedArtifactId = null;
        clearVersionAndPreview();

        MavenSearchService.search(project, currentQuery, page,
                result -> {
                    totalPages = result.pageCount();
                    totalResults = result.totalCount();

                    tableModel.setRowCount(0);
                    if (result.entries().isEmpty()) {
                        resultTable.getEmptyText().setText(EMPTY_TEXT_NO_RESULTS);
                    } else {
                        for (ArtifactEntry entry : result.entries()) {
                            tableModel.addRow(new Object[]{entry.groupId(), entry.artifactId(), entry.latestVersion()});
                        }
                    }
                    clearBtn.setEnabled(!result.entries().isEmpty());
                    updatePaginationUI();
                    clearVersionAndPreview();
                },
                error -> Messages.showErrorDialog(error, "Search Error"),
                () -> {
                    isSearching = false;
                    searchBtn.setEnabled(true);
                }
        );
    }

    private void updatePaginationUI() {
        if (totalPages <= 1) {
            pageNumField.setText(totalResults > 0 ? "1" : "0");
            pageNumField.setEnabled(false);
            pageTotalLabel.setText("/ " + (Math.max(totalPages, 0)) + "  (" + totalResults + " results)");
            prevPageBtn.setEnabled(false);
            nextPageBtn.setEnabled(false);
        } else {
            pageNumField.setText(String.valueOf(currentPage + 1));
            pageNumField.setEnabled(true);
            pageTotalLabel.setText("/ " + totalPages + "  (" + totalResults + " results)");
            prevPageBtn.setEnabled(currentPage > 0);
            nextPageBtn.setEnabled(currentPage < totalPages - 1);
        }
    }

    private void jumpToPage() {
        if (totalPages <= 1) return;
        try {
            int page = Integer.parseInt(pageNumField.getText().trim()) - 1;
            if (page >= 0 && page < totalPages && page != currentPage) {
                goToPage(page);
                return;
            }
        } catch (NumberFormatException ignored) {
        }
        pageNumField.setText(String.valueOf(currentPage + 1));
    }

    // ========================================================================
    //  Feature B: Version Fetching
    // ========================================================================
    private void fetchVersions(String groupId, String artifactId) {
        versionCombo.removeAllItems();
        versionCombo.addItem(LOADING_TEXT);
        versionCombo.setEnabled(false);

        MavenSearchService.fetchVersions(project, groupId, artifactId,
                versions -> {
                    String selectedVersion = (String) versionCombo.getSelectedItem();

                    versionCombo.removeAllItems();
                    versionCombo.addItem(NO_VERSION_ITEM);
                    for (String v : versions) {
                        versionCombo.addItem(v);
                    }

                    if (selectedVersion != null) {
                        for (int i = 0; i < versionCombo.getItemCount(); i++) {
                            if (versionCombo.getItemAt(i).equals(selectedVersion)) {
                                versionCombo.setSelectedIndex(i);
                                break;
                            }
                        }
                    }

                    updateXmlPreview();
                },
                error -> {
                    Messages.showErrorDialog(error, "Version Load Error");
                    versionCombo.removeAllItems();
                    versionCombo.addItem(NO_VERSION_ITEM);
                },
                () -> versionCombo.setEnabled(true)
        );
    }

    // ========================================================================
    //  Feature C: XML Preview
    // ========================================================================
    private void updateXmlPreview() {
        if (selectedGroupId == null || selectedArtifactId == null) {
            xmlPreview.setText("");
            copyBtn.setEnabled(false);
            return;
        }

        String groupId = selectedGroupId;
        String artifactId = selectedArtifactId;
        String version = (String) versionCombo.getSelectedItem();
        boolean hasVersion = version != null && !NO_VERSION_ITEM.equals(version);

        String depXml = StringUtils.buildDependencyXml(groupId, artifactId, hasVersion ? version : null, "    ");
        xmlPreview.setText(depXml);
        xmlPreview.setCaretPosition(0);
        copyBtn.setEnabled(true);
    }

    // ========================================================================
    //  Feature D: Write to pom.xml (PSI)
    // ========================================================================
    private void addDependencyToPom() {
        if (project == null || selectedGroupId == null || selectedArtifactId == null) {
            LOG.warn("addDependencyToPom canceled: project=" + (project != null)
                    + ", groupId=" + selectedGroupId + ", artifactId=" + selectedArtifactId);
            return;
        }

        VirtualFile pomFile = PomXmlManager.findPomFile(project);
        if (pomFile == null) {
            Messages.showWarningDialog("No pom.xml file is currently open in the editor.",
                    "Add to pom.xml");
            return;
        }

        String groupId = selectedGroupId;
        String artifactId = selectedArtifactId;
        String version = (String) versionCombo.getSelectedItem();
        boolean hasVersion = version != null && !NO_VERSION_ITEM.equals(version);

        try {
            PomXmlManager.addDependency(project, pomFile, groupId, artifactId,
                    hasVersion ? version : null);
            Messages.showInfoMessage("Dependency added to pom.xml successfully!",
                    "Add to pom.xml");
        } catch (Exception ex) {
            Messages.showErrorDialog("Failed to add dependency: " + ex.getMessage(), "Error");
        }
    }

    // ========================================================================
    //  Helper Methods
    // ========================================================================
    private void clearVersionAndPreview() {
        versionCombo.removeAllItems();
        versionCombo.addItem(NO_VERSION_ITEM);
        versionCombo.setEnabled(false);
        xmlPreview.setText("");
        copyBtn.setEnabled(false);
        selectedGroupId = null;
        selectedArtifactId = null;
    }

    // ========================================================================
    //  Override default dialog buttons
    // ========================================================================
    @Override
    protected Action @NotNull [] createActions() {
        return new Action[0]; // Use only custom buttons inside the panel
    }

}
