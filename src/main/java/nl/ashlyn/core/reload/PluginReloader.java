package nl.ashlyn.core.reload;

import nl.ashlyn.core.Core;
import nl.ashlyn.core.utils.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.io.Closeable;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Created by Ashlyn on 27/10/2026
 *
 * Swaps a plugin for the newest jar with the same name in the plugins folder, without a server restart.
 * Plugins that depend on it are reloaded with it, so they pick up the new classes.
 * Paper has no API for unloading, so the plugin is taken out of Paper's lists with reflection.
 */
public class PluginReloader {

    /**
     * Reload a plugin and everything that depends on it on the next tick
     * @param plugin The plugin to reload
     * @param sender Who gets the result
     */
    public static void reload(Plugin plugin, CommandSender sender) {
        String name = plugin.getName();
        Bukkit.getScheduler().runTask(Core.getInstance(), () -> run(name, sender));
    }

    private static void run(String name, CommandSender sender) {
        PluginManager manager = Bukkit.getPluginManager();
        Plugin target = manager.getPlugin(name);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + name + " is niet geladen.");
            return;
        }

        List<Plugin> plugins = withDependents(target);
        Map<Plugin, File> jars = new LinkedHashMap<>();
        for (Plugin plugin : plugins) {
            File jar = findJar(plugin.getName());
            if (jar == null) {
                sender.sendMessage(ChatColor.RED + "Geen jar gevonden voor " + plugin.getName() + ".");
                return;
            }
            jars.put(plugin, jar);
        }

        List<Plugin> reversed = new ArrayList<>(plugins);
        Collections.reverse(reversed);
        List<ClassLoader> oldLoaders = new ArrayList<>();
        for (Plugin plugin : reversed) {
            if (plugin.isEnabled()) manager.disablePlugin(plugin);
            oldLoaders.add(unload(plugin));
        }

        List<String> reloaded = new ArrayList<>();
        for (Map.Entry<Plugin, File> entry : jars.entrySet()) {
            String pluginName = entry.getKey().getName();
            try {
                Plugin fresh = manager.loadPlugin(entry.getValue());
                if (fresh == null) throw new IllegalStateException("loadPlugin gaf niets terug");
                manager.enablePlugin(fresh);
                reloaded.add(fresh.getName() + " v" + fresh.getDescription().getVersion());
            } catch (Throwable e) {
                Logger.logCore(ChatColor.RED + "Reload of " + pluginName + " failed: " + e);
                e.printStackTrace();
                sender.sendMessage(ChatColor.RED + pluginName + " kon niet laden: " + e.getMessage());
            }
        }

        syncCommands();
        sender.sendMessage(ChatColor.GREEN + "Herladen: " + String.join(", ", reloaded));
        Logger.logCore("Reloaded jars: " + String.join(", ", reloaded));

        for (ClassLoader loader : oldLoaders) {
            if (loader instanceof Closeable closeable) {
                try {
                    closeable.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static List<Plugin> withDependents(Plugin target) {
        List<Plugin> order = new ArrayList<>();
        order.add(target);
        for (int i = 0; i < order.size(); i++) {
            String name = order.get(i).getName();
            for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
                if (order.contains(plugin)) continue;
                if (plugin.getDescription().getDepend().contains(name)
                        || plugin.getDescription().getSoftDepend().contains(name)) {
                    order.add(plugin);
                }
            }
        }
        return order;
    }

    private static File findJar(String name) {
        File[] files = Core.getInstance().getDataFolder().getParentFile().listFiles();
        if (files == null) return null;
        File newest = null;
        for (File file : files) {
            if (!file.isFile() || !file.getName().endsWith(".jar")) continue;
            if (!name.equals(jarPluginName(file))) continue;
            if (newest == null || file.lastModified() > newest.lastModified()) newest = file;
        }
        return newest;
    }

    private static String jarPluginName(File file) {
        try (JarFile jar = new JarFile(file)) {
            JarEntry entry = jar.getJarEntry("plugin.yml");
            if (entry == null) return null;
            try (InputStreamReader reader = new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader).getString("name");
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static ClassLoader unload(Plugin plugin) {
        ClassLoader loader = plugin.getClass().getClassLoader();
        removeCommands(plugin);
        for (Permission permission : plugin.getDescription().getPermissions()) {
            Bukkit.getPluginManager().removePermission(permission);
        }

        List<String> names = new ArrayList<>();
        names.add(plugin.getName());
        names.add(plugin.getName().toLowerCase());
        names.addAll(plugin.getDescription().getProvides());
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        scrub(Bukkit.getPluginManager(), plugin, loader, names, visited, 0);
        scrub(plugin.getPluginLoader(), plugin, loader, names, visited, 0);
        unregisterLoader(loader);
        return loader;
    }

    private static void scrub(Object holder, Plugin plugin, ClassLoader loader, List<String> names, Set<Object> visited, int depth) {
        if (holder == null || depth > 4 || !visited.add(holder)) return;
        for (Class<?> type = holder.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                Object value;
                try {
                    field.setAccessible(true);
                    value = field.get(holder);
                } catch (Exception e) {
                    continue;
                }
                if (value == null) continue;

                try {
                    if (value instanceof Collection<?> collection) {
                        collection.removeIf(item -> item == plugin || item == loader);
                    } else if (value instanceof Map<?, ?> map) {
                        List<Object> keys = new ArrayList<>();
                        for (Map.Entry<?, ?> entry : map.entrySet()) {
                            if (entry.getValue() == plugin || entry.getValue() == loader || entry.getKey() == plugin) {
                                keys.add(entry.getKey());
                            }
                        }
                        for (Object key : keys) map.remove(key);
                    } else if (isGraph(value)) {
                        Method removeNode = graphType(value).getMethod("removeNode", Object.class);
                        for (String name : names) removeNode.invoke(value, name);
                    } else if (isPluginInternal(value)) {
                        scrub(value, plugin, loader, names, visited, depth + 1);
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static boolean isGraph(Object value) {
        return graphType(value) != null;
    }

    private static Class<?> graphType(Object value) {
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            for (Class<?> face : type.getInterfaces()) {
                if (face.getName().endsWith(".graph.MutableGraph")) return face;
            }
        }
        return null;
    }

    private static boolean isPluginInternal(Object value) {
        String name = value.getClass().getName();
        return name.startsWith("io.papermc.paper.plugin.") || name.startsWith("org.bukkit.plugin.");
    }

    private static void unregisterLoader(ClassLoader loader) {
        try {
            Class<?> storage = Class.forName("io.papermc.paper.plugin.provider.classloader.PaperClassLoaderStorage",
                    true, Bukkit.class.getClassLoader());
            Object instance = storage.getMethod("instance").invoke(null);
            for (Method method : storage.getMethods()) {
                if (method.getName().equals("unregisterClassloader") && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isInstance(loader)) {
                    method.invoke(instance, loader);
                }
            }
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private static void removeCommands(Plugin plugin) {
        try {
            Object commandMap = Bukkit.getServer().getClass().getMethod("getCommandMap").invoke(Bukkit.getServer());
            Map<String, Command> known = (Map<String, Command>) commandMap.getClass().getMethod("getKnownCommands").invoke(commandMap);
            List<String> keys = new ArrayList<>();
            List<Command> commands = new ArrayList<>();
            for (Map.Entry<String, Command> entry : known.entrySet()) {
                if (entry.getValue() instanceof PluginIdentifiableCommand command && command.getPlugin() == plugin) {
                    keys.add(entry.getKey());
                    if (!commands.contains(entry.getValue())) commands.add(entry.getValue());
                }
            }
            for (String key : keys) known.remove(key);
            for (Command command : commands) command.unregister((org.bukkit.command.CommandMap) commandMap);
        } catch (Exception e) {
            Logger.logCore(ChatColor.RED + "Could not remove commands of " + plugin.getName() + ": " + e);
        }
    }

    private static void syncCommands() {
        try {
            Method sync = Bukkit.getServer().getClass().getDeclaredMethod("syncCommands");
            sync.setAccessible(true);
            sync.invoke(Bukkit.getServer());
        } catch (Exception ignored) {
        }
        for (Player player : Bukkit.getOnlinePlayers()) player.updateCommands();
    }
}
