package cc.xiaowei.service;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;

public class PomXmlManager {

    private static final Logger LOG = Logger.getInstance(PomXmlManager.class);

    public static @Nullable VirtualFile findPomFile(Project project) {
        FileEditorManager fem = FileEditorManager.getInstance(project);
        VirtualFile[] selectedFiles = fem.getSelectedFiles();
        if (selectedFiles.length > 0 && "pom.xml".equals(selectedFiles[0].getName())) {
            return selectedFiles[0];
        }
        String basePath = project.getBasePath();
        if (basePath != null) {
            VirtualFile baseDir = LocalFileSystem.getInstance().findFileByPath(basePath);
            if (baseDir != null) return baseDir.findChild("pom.xml");
        }
        return null;
    }

    /**
     * Adds a Maven dependency to the given pom.xml file using PSI.
     * Creates the {@code <dependencies>} block if it doesn't exist.
     *
     * @throws RuntimeException wrapping any underlying PSI exception
     */
    public static void addDependency(Project project, VirtualFile pomFile,
                                     String groupId, String artifactId,
                                     @Nullable String version) {
        LOG.info("=== Add Dependency to pom.xml ===");
        LOG.info("Adding " + groupId + ":" + artifactId + " to pom.xml");

        String depXmlStr = cc.xiaowei.utils.StringUtils.buildDependencyXml(
                groupId, artifactId, version, "  ");
        LOG.info("Creating dependency XML:\n" + depXmlStr);

        WriteCommandAction.runWriteCommandAction(project, null, null, () -> {
            try {
                PsiFile psiFile = PsiManager.getInstance(project).findFile(pomFile);
                if (!(psiFile instanceof XmlFile xmlFile)) {
                    throw new RuntimeException("The file is not a valid XML file.");
                }

                XmlTag rootTag = xmlFile.getRootTag();
                if (rootTag == null || !"project".equals(rootTag.getName())) {
                    throw new RuntimeException("Invalid pom.xml: root tag is not <project>.");
                }

                XmlTag dependenciesTag = rootTag.findFirstSubTag("dependencies");
                if (dependenciesTag == null) {
                    LOG.info("<dependencies> not found in pom.xml, creating one");
                    dependenciesTag = rootTag.createChildTag("dependencies", null, null, false);
                    dependenciesTag = rootTag.addSubTag(dependenciesTag, true);
                }

                XmlTag newDependencyTag = XmlElementFactory.getInstance(project)
                        .createTagFromText(depXmlStr);
                XmlTag addedTag = dependenciesTag.addSubTag(newDependencyTag, false);
                CodeStyleManager.getInstance(project).reformat(addedTag);

                LOG.info("Dependency added to pom.xml successfully");
            } catch (Exception ex) {
                LOG.error("Failed to add dependency to pom.xml", ex);
                throw new RuntimeException("Failed to add dependency: " + ex.getMessage(), ex);
            }
        });
    }
}
