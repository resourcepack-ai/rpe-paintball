package ai.resourcepack.addons.paintball.command;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Perms} and {@code plugin.yml} have to agree.
 *
 * <p>Nothing at runtime notices when they do not: a node the code checks but
 * {@code plugin.yml} never declares gets Bukkit's default (op only), and one
 * missing from a parent's children is a command the server owner who granted
 * {@code paintball.admin} reasonably expected and does not have. Read as text,
 * since there is no YAML library on the test classpath worth adding for it.
 */
class PermsTest {

    private static String pluginYml() throws IOException {
        return Files.readString(Path.of("src", "main", "resources", "plugin.yml")).replace("\r\n", "\n");
    }

    /** Every String constant in {@link Perms}. */
    private static Set<String> checkedNodes() throws IllegalAccessException {
        Set<String> out = new LinkedHashSet<>();
        for (Field f : Perms.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) out.add((String) f.get(null));
        }
        return out;
    }

    /** Every {@code paintball.x:} node declared under {@code permissions:}. */
    private static Set<String> declaredNodes(String yml) {
        Set<String> nodes = new LinkedHashSet<>();
        Matcher m = Pattern.compile("(?m)^  (paintball\\.[a-z.]+):$").matcher(yml);
        while (m.find()) nodes.add(m.group(1));
        return nodes;
    }

    /** The children a parent node grants. */
    private static Set<String> children(String yml, String parent) {
        int at = yml.indexOf("\n  " + parent + ":\n");
        assertTrue(at >= 0, parent + " is not declared");
        int end = yml.indexOf("\n  paintball.", at + 1);
        String block = end < 0 ? yml.substring(at) : yml.substring(at, end);
        Set<String> out = new LinkedHashSet<>();
        Matcher m = Pattern.compile("(?m)^      (paintball\\.[a-z.]+): true$").matcher(block);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    @Test
    void everyNodeTheCodeChecksIsDeclared() throws Exception {
        Set<String> declared = declaredNodes(pluginYml());
        for (String node : checkedNodes()) assertTrue(declared.contains(node), node + " is checked but not in plugin.yml");
    }

    @Test
    void everyDeclaredNodeIsCheckedOrAParent() throws Exception {
        Set<String> checked = checkedNodes();
        for (String node : declaredNodes(pluginYml())) {
            if (node.equals("paintball.play") || node.equals("paintball.admin")) continue;
            assertTrue(checked.contains(node), node + " is in plugin.yml but nothing checks it");
        }
    }

    @Test
    void theParentsGrantExactlyTheirLists() throws Exception {
        String yml = pluginYml();
        assertEquals(new LinkedHashSet<>(Perms.PLAY), children(yml, "paintball.play"));
        assertEquals(new LinkedHashSet<>(Perms.ADMIN), children(yml, "paintball.admin"));
    }

    @Test
    void nothingChecksAParent() throws Exception {
        Set<String> checked = checkedNodes();
        assertFalse(checked.contains("paintball.play"));
        assertFalse(checked.contains("paintball.admin"));
    }

    @Test
    void theCommandHasNoBlanketPermission() throws IOException {
        String yml = pluginYml();
        String commands = yml.substring(yml.indexOf("commands:"), yml.indexOf("permissions:"));
        assertFalse(commands.matches("(?s).*\\n\\s+permission:.*"),
                "/paintball must not carry a command-level permission; each subcommand checks its own");
    }
}
