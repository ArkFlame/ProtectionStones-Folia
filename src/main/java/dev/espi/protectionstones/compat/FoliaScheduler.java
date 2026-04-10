package dev.espi.protectionstones.compat;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class FoliaScheduler {
    private static final Set<CompatTask> TASKS = Collections.newSetFromMap(new ConcurrentHashMap<CompatTask, Boolean>());

    private static JavaPlugin plugin;
    private static boolean folia;
    private static Object asyncScheduler;
    private static Object globalRegionScheduler;
    private static Object regionScheduler;
    private static Method serverGetAsyncScheduler;
    private static Method serverGetGlobalRegionScheduler;
    private static Method serverGetRegionScheduler;
    private static Method entityGetScheduler;
    private static Method playerTeleportAsync;
    private static Method asyncSchedulerRunNow;
    private static Method asyncSchedulerRunDelayed;
    private static Method asyncSchedulerRunAtFixedRate;
    private static Method asyncSchedulerCancelTasks;
    private static Method globalRun;
    private static Method globalRunDelayed;
    private static Method globalRunAtFixedRate;
    private static Method globalCancelTasks;
    private static Method regionExecuteLocation;
    private static Method regionExecuteChunk;
    private static Method regionRunLocation;
    private static Method regionRunLocationDelayed;
    private static Method regionRunLocationAtFixedRate;
    private static Method regionRunChunk;
    private static Method regionRunChunkDelayed;
    private static Method regionRunChunkAtFixedRate;
    private static Method entityRun;
    private static Method entityRunDelayed;
    private static Method entityRunAtFixedRate;
    private static Method entityExecute;
    private static Method scheduledTaskCancel;
    private static Method scheduledTaskIsCancelled;

    private FoliaScheduler() {
    }

    public static void init(JavaPlugin plugin) {
        shutdown();
        FoliaScheduler.plugin = Objects.requireNonNull(plugin, "plugin");
        folia = isFoliaRuntime();
        if (!folia) {
            return;
        }
        try {
            Server server = Bukkit.getServer();
            serverGetAsyncScheduler = server.getClass().getMethod("getAsyncScheduler");
            serverGetGlobalRegionScheduler = server.getClass().getMethod("getGlobalRegionScheduler");
            serverGetRegionScheduler = server.getClass().getMethod("getRegionScheduler");

            asyncScheduler = serverGetAsyncScheduler.invoke(server);
            globalRegionScheduler = serverGetGlobalRegionScheduler.invoke(server);
            regionScheduler = serverGetRegionScheduler.invoke(server);

            asyncSchedulerRunNow = asyncScheduler.getClass().getMethod("runNow", JavaPlugin.class, Consumer.class);
            asyncSchedulerRunDelayed = asyncScheduler.getClass().getMethod("runDelayed", JavaPlugin.class, Consumer.class, long.class, TimeUnit.class);
            asyncSchedulerRunAtFixedRate = asyncScheduler.getClass().getMethod("runAtFixedRate", JavaPlugin.class, Consumer.class, long.class, long.class, TimeUnit.class);
            asyncSchedulerCancelTasks = asyncScheduler.getClass().getMethod("cancelTasks", JavaPlugin.class);

            globalRun = globalRegionScheduler.getClass().getMethod("run", JavaPlugin.class, Consumer.class);
            globalRunDelayed = globalRegionScheduler.getClass().getMethod("runDelayed", JavaPlugin.class, Consumer.class, long.class);
            globalRunAtFixedRate = globalRegionScheduler.getClass().getMethod("runAtFixedRate", JavaPlugin.class, Consumer.class, long.class, long.class);
            globalCancelTasks = globalRegionScheduler.getClass().getMethod("cancelTasks", JavaPlugin.class);

            regionExecuteLocation = regionScheduler.getClass().getMethod("execute", JavaPlugin.class, Location.class, Runnable.class);
            regionExecuteChunk = regionScheduler.getClass().getMethod("execute", JavaPlugin.class, World.class, int.class, int.class, Runnable.class);
            regionRunLocation = regionScheduler.getClass().getMethod("run", JavaPlugin.class, Location.class, Consumer.class);
            regionRunLocationDelayed = regionScheduler.getClass().getMethod("runDelayed", JavaPlugin.class, Location.class, Consumer.class, long.class);
            regionRunLocationAtFixedRate = regionScheduler.getClass().getMethod("runAtFixedRate", JavaPlugin.class, Location.class, Consumer.class, long.class, long.class);
            regionRunChunk = regionScheduler.getClass().getMethod("run", JavaPlugin.class, World.class, int.class, int.class, Consumer.class);
            regionRunChunkDelayed = regionScheduler.getClass().getMethod("runDelayed", JavaPlugin.class, World.class, int.class, int.class, Consumer.class, long.class);
            regionRunChunkAtFixedRate = regionScheduler.getClass().getMethod("runAtFixedRate", JavaPlugin.class, World.class, int.class, int.class, Consumer.class, long.class, long.class);

            entityGetScheduler = Entity.class.getMethod("getScheduler");
            Class<?> entityScheduler = entityGetScheduler.getReturnType();
            entityRun = entityScheduler.getMethod("run", JavaPlugin.class, Consumer.class, Runnable.class);
            entityRunDelayed = entityScheduler.getMethod("runDelayed", JavaPlugin.class, Consumer.class, Runnable.class, long.class);
            entityRunAtFixedRate = entityScheduler.getMethod("runAtFixedRate", JavaPlugin.class, Consumer.class, Runnable.class, long.class, long.class);
            entityExecute = entityScheduler.getMethod("execute", JavaPlugin.class, Runnable.class, Runnable.class);
            scheduledTaskCancel = entityRun.getReturnType().getMethod("cancel");
            scheduledTaskIsCancelled = entityRun.getReturnType().getMethod("isCancelled");

            playerTeleportAsync = Player.class.getMethod("teleportAsync", Location.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize Folia scheduler bridge", e);
        }
    }

    public static void shutdown() {
        TASKS.clear();
        plugin = null;
        folia = false;
        asyncScheduler = null;
        globalRegionScheduler = null;
        regionScheduler = null;
        serverGetAsyncScheduler = null;
        serverGetGlobalRegionScheduler = null;
        serverGetRegionScheduler = null;
        entityGetScheduler = null;
        playerTeleportAsync = null;
        asyncSchedulerRunNow = null;
        asyncSchedulerRunDelayed = null;
        asyncSchedulerRunAtFixedRate = null;
        asyncSchedulerCancelTasks = null;
        globalRun = null;
        globalRunDelayed = null;
        globalRunAtFixedRate = null;
        globalCancelTasks = null;
        regionExecuteLocation = null;
        regionExecuteChunk = null;
        regionRunLocation = null;
        regionRunLocationDelayed = null;
        regionRunLocationAtFixedRate = null;
        regionRunChunk = null;
        regionRunChunkDelayed = null;
        regionRunChunkAtFixedRate = null;
        entityRun = null;
        entityRunDelayed = null;
        entityRunAtFixedRate = null;
        entityExecute = null;
        scheduledTaskCancel = null;
        scheduledTaskIsCancelled = null;
    }

    public static boolean isFolia() {
        return folia;
    }

    public static CompatTask runAsync(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (!folia) {
            return register(new BukkitCompatTask(Bukkit.getScheduler().runTaskAsynchronously(requirePlugin(), task)));
        }
        return invokeAsync(task);
    }

    public static CompatTask runAsyncLater(Runnable task, long delayTicks) {
        Objects.requireNonNull(task, "task");
        if (!folia) {
            return register(new BukkitCompatTask(Bukkit.getScheduler().runTaskLaterAsynchronously(requirePlugin(), task, delayTicks)));
        }
        return invokeAsyncLater(task, delayTicks);
    }

    public static CompatTask runAsyncTimer(Runnable task, long initialDelayTicks, long periodTicks) {
        Objects.requireNonNull(task, "task");
        if (!folia) {
            return register(new BukkitCompatTask(Bukkit.getScheduler().runTaskTimerAsynchronously(requirePlugin(), task, initialDelayTicks, periodTicks)));
        }
        return invokeAsyncTimer(task, initialDelayTicks, periodTicks);
    }

    public static CompatTask runGlobal(Runnable task) {
        Objects.requireNonNull(task, "task");
        if (!folia) {
            return register(new BukkitCompatTask(Bukkit.getScheduler().runTask(requirePlugin(), task)));
        }
        return invokeGlobal(task);
    }

    public static CompatTask runGlobalLater(Runnable task, long delayTicks) {
        Objects.requireNonNull(task, "task");
        if (!folia) {
            return register(new BukkitCompatTask(Bukkit.getScheduler().runTaskLater(requirePlugin(), task, delayTicks)));
        }
        return invokeGlobalLater(task, delayTicks);
    }

    public static CompatTask runGlobalTimer(Runnable task, long initialDelayTicks, long periodTicks) {
        Objects.requireNonNull(task, "task");
        if (!folia) {
            return register(new BukkitCompatTask(Bukkit.getScheduler().runTaskTimer(requirePlugin(), task, initialDelayTicks, periodTicks)));
        }
        return invokeGlobalTimer(task, initialDelayTicks, periodTicks);
    }

    public static CompatTask runRegion(Location location, Runnable task) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(task, "task");
        if (!folia) {
            return register(new BukkitCompatTask(Bukkit.getScheduler().runTask(requirePlugin(), task)));
        }
        return invokeRegion(location, task);
    }

    public static CompatTask runRegion(Chunk chunk, Runnable task) {
        Objects.requireNonNull(chunk, "chunk");
        Objects.requireNonNull(task, "task");
        if (!folia) {
            return register(new BukkitCompatTask(Bukkit.getScheduler().runTask(requirePlugin(), task)));
        }
        return invokeRegion(chunk, task);
    }

    public static CompatTask runRegionLater(Location location, Runnable task, long delayTicks) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(task, "task");
        if (!folia) {
            return register(new BukkitCompatTask(Bukkit.getScheduler().runTaskLater(requirePlugin(), task, delayTicks)));
        }
        return invokeRegionLater(location, task, delayTicks);
    }

    public static CompatTask runRegionTimer(Location location, Runnable task, long initialDelayTicks, long periodTicks) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(task, "task");
        if (!folia) {
            return register(new BukkitCompatTask(Bukkit.getScheduler().runTaskTimer(requirePlugin(), task, initialDelayTicks, periodTicks)));
        }
        return invokeRegionTimer(location, task, initialDelayTicks, periodTicks);
    }

    public static CompatTask runEntity(Entity entity, Runnable task) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(task, "task");
        if (!folia) {
            return register(new BukkitCompatTask(Bukkit.getScheduler().runTask(requirePlugin(), task)));
        }
        return invokeEntity(entity, task, null);
    }

    public static CompatTask runEntityLater(Entity entity, Runnable task, Runnable retired, long delayTicks) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(task, "task");
        if (!folia) {
            return register(new BukkitCompatTask(Bukkit.getScheduler().runTaskLater(requirePlugin(), task, delayTicks)));
        }
        return invokeEntityLater(entity, task, retired, delayTicks);
    }

    public static CompatTask runEntityTimer(Entity entity, Runnable task, Runnable retired, long initialDelayTicks, long periodTicks) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(task, "task");
        if (!folia) {
            return register(new BukkitCompatTask(Bukkit.getScheduler().runTaskTimer(requirePlugin(), task, initialDelayTicks, periodTicks)));
        }
        return invokeEntityTimer(entity, task, retired, initialDelayTicks, periodTicks);
    }

    public static <T> CompletableFuture<T> callGlobal(final Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        final CompletableFuture<T> future = new CompletableFuture<T>();
        runGlobal(new Runnable() {
            @Override
            public void run() {
                completeFuture(future, supplier);
            }
        });
        return future;
    }

    public static <T> CompletableFuture<T> callRegion(final Location location, final Supplier<T> supplier) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(supplier, "supplier");
        final CompletableFuture<T> future = new CompletableFuture<T>();
        runRegion(location, new Runnable() {
            @Override
            public void run() {
                completeFuture(future, supplier);
            }
        });
        return future;
    }

    public static <T> CompletableFuture<T> callEntity(final Entity entity, final Supplier<T> supplier) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(supplier, "supplier");
        final CompletableFuture<T> future = new CompletableFuture<T>();
        if (!folia) {
            runGlobal(new Runnable() {
                @Override
                public void run() {
                    completeFuture(future, supplier);
                }
            });
            return future;
        }
        try {
            Object scheduler = entityGetScheduler.invoke(entity);
            if (scheduler == null) {
                future.completeExceptionally(new IllegalStateException("Entity scheduler is unavailable"));
                return future;
            }
            Object result = entityExecute.invoke(scheduler, requirePlugin(), new Runnable() {
                @Override
                public void run() {
                    completeFuture(future, supplier);
                }
            }, new Runnable() {
                @Override
                public void run() {
                    if (!future.isDone()) {
                        future.completeExceptionally(new IllegalStateException("Entity is retired"));
                    }
                }
            });
            if (result instanceof Boolean && !((Boolean) result).booleanValue() && !future.isDone()) {
                future.completeExceptionally(new IllegalStateException("Entity is retired"));
            }
            return future;
        } catch (Exception e) {
            future.completeExceptionally(e);
            return future;
        }
    }

    public static CompletableFuture<Boolean> teleportPlayer(final Player player, final Location location) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(location, "location");
        if (!folia) {
            final CompletableFuture<Boolean> future = new CompletableFuture<Boolean>();
            runGlobal(new Runnable() {
                @Override
                public void run() {
                    try {
                        future.complete(player.teleport(location));
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    }
                }
            });
            return future;
        }
        try {
            @SuppressWarnings("unchecked")
            CompletableFuture<Boolean> future = (CompletableFuture<Boolean>) playerTeleportAsync.invoke(player, location);
            return future;
        } catch (Exception e) {
            CompletableFuture<Boolean> failed = new CompletableFuture<Boolean>();
            failed.completeExceptionally(e);
            return failed;
        }
    }

    public static void cancelPluginTasks() {
        JavaPlugin currentPlugin = plugin;
        if (currentPlugin == null) {
            return;
        }
        for (CompatTask task : TASKS.toArray(new CompatTask[0])) {
            task.cancel();
        }
        TASKS.clear();
        if (!folia) {
            Bukkit.getScheduler().cancelTasks(currentPlugin);
            return;
        }
        try {
            asyncSchedulerCancelTasks.invoke(asyncScheduler, currentPlugin);
            globalCancelTasks.invoke(globalRegionScheduler, currentPlugin);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to cancel Folia scheduler tasks", e);
        }
    }

    public static boolean isOwnedByCurrentRegion(Location location) {
        if (location == null || !folia) {
            return true;
        }
        return invokeOwnershipHelper(location, Location.class);
    }

    public static boolean isOwnedByCurrentRegion(Entity entity) {
        if (entity == null || !folia) {
            return true;
        }
        return invokeOwnershipHelper(entity, Entity.class);
    }

    private static boolean isFoliaRuntime() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static JavaPlugin requirePlugin() {
        JavaPlugin currentPlugin = plugin;
        if (currentPlugin == null) {
            throw new IllegalStateException("FoliaScheduler has not been initialized");
        }
        return currentPlugin;
    }

    private static CompatTask register(CompatTask task) {
        TASKS.add(task);
        return task;
    }

    private static <T> void completeFuture(CompletableFuture<T> future, Supplier<T> supplier) {
        try {
            future.complete(supplier.get());
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
    }

    private static CompatTask invokeAsync(Runnable task) {
        return register(new FoliaCompatTask(invokeScheduler(asyncSchedulerRunNow, asyncScheduler, requirePlugin(), consumer(task))));
    }

    private static CompatTask invokeAsyncLater(Runnable task, long delayTicks) {
        long delayMillis = delayTicks * 50L;
        return register(new FoliaCompatTask(invokeScheduler(asyncSchedulerRunDelayed, asyncScheduler, requirePlugin(), consumer(task), delayMillis, TimeUnit.MILLISECONDS)));
    }

    private static CompatTask invokeAsyncTimer(Runnable task, long initialDelayTicks, long periodTicks) {
        long initialDelayMillis = initialDelayTicks * 50L;
        long periodMillis = periodTicks * 50L;
        return register(new FoliaCompatTask(invokeScheduler(asyncSchedulerRunAtFixedRate, asyncScheduler, requirePlugin(), consumer(task), initialDelayMillis, periodMillis, TimeUnit.MILLISECONDS)));
    }

    private static CompatTask invokeGlobal(Runnable task) {
        return register(new FoliaCompatTask(invokeScheduler(globalRun, globalRegionScheduler, requirePlugin(), consumer(task))));
    }

    private static CompatTask invokeGlobalLater(Runnable task, long delayTicks) {
        return register(new FoliaCompatTask(invokeScheduler(globalRunDelayed, globalRegionScheduler, requirePlugin(), consumer(task), delayTicks)));
    }

    private static CompatTask invokeGlobalTimer(Runnable task, long initialDelayTicks, long periodTicks) {
        return register(new FoliaCompatTask(invokeScheduler(globalRunAtFixedRate, globalRegionScheduler, requirePlugin(), consumer(task), initialDelayTicks, periodTicks)));
    }

    private static CompatTask invokeRegion(Location location, Runnable task) {
        return register(new FoliaCompatTask(invokeScheduler(regionRunLocation, regionScheduler, requirePlugin(), location, consumer(task))));
    }

    private static CompatTask invokeRegion(Chunk chunk, Runnable task) {
        World world = chunk.getWorld();
        if (world == null) {
            throw new IllegalStateException("Chunk world is unavailable");
        }
        return register(new FoliaCompatTask(invokeScheduler(regionRunChunk, regionScheduler, requirePlugin(), world, chunk.getX(), chunk.getZ(), consumer(task))));
    }

    private static CompatTask invokeRegionLater(Location location, Runnable task, long delayTicks) {
        return register(new FoliaCompatTask(invokeScheduler(regionRunLocationDelayed, regionScheduler, requirePlugin(), location, consumer(task), delayTicks)));
    }

    private static CompatTask invokeRegionTimer(Location location, Runnable task, long initialDelayTicks, long periodTicks) {
        return register(new FoliaCompatTask(invokeScheduler(regionRunLocationAtFixedRate, regionScheduler, requirePlugin(), location, consumer(task), initialDelayTicks, periodTicks)));
    }

    private static CompatTask invokeEntity(Entity entity, Runnable task, Runnable retired) {
        Object scheduler = getEntityScheduler(entity);
        return register(new FoliaCompatTask(invokeScheduler(entityRun, scheduler, requirePlugin(), consumer(task), retiredCallback(retired))));
    }

    private static CompatTask invokeEntityLater(Entity entity, Runnable task, Runnable retired, long delayTicks) {
        Object scheduler = getEntityScheduler(entity);
        return register(new FoliaCompatTask(invokeScheduler(entityRunDelayed, scheduler, requirePlugin(), consumer(task), retiredCallback(retired), normalizeEntityDelay(delayTicks))));
    }

    private static CompatTask invokeEntityTimer(Entity entity, Runnable task, Runnable retired, long initialDelayTicks, long periodTicks) {
        Object scheduler = getEntityScheduler(entity);
        return register(new FoliaCompatTask(invokeScheduler(entityRunAtFixedRate, scheduler, requirePlugin(), consumer(task), retiredCallback(retired), normalizeEntityDelay(initialDelayTicks), normalizeEntityDelay(periodTicks))));
    }

    private static Object getEntityScheduler(Entity entity) {
        try {
            return entityGetScheduler.invoke(entity);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to resolve entity scheduler", e);
        }
    }

    private static Object invokeScheduler(Method method, Object target, Object... args) {
        try {
            Object result = method.invoke(target, args);
            if (result == null) {
                throw new IllegalStateException("Scheduler invocation returned null: " + method.getName());
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to invoke scheduler method: " + method.getName(), e);
        }
    }

    private static Consumer<Object> consumer(final Runnable task) {
        return new Consumer<Object>() {
            @Override
            public void accept(Object ignored) {
                task.run();
            }
        };
    }

    private static Runnable retiredCallback(final Runnable retired) {
        if (retired == null) {
            return new Runnable() {
                @Override
                public void run() {
                }
            };
        }
        return retired;
    }

    private static long normalizeEntityDelay(long delayTicks) {
        return delayTicks < 1L ? 1L : delayTicks;
    }

    private static boolean invokeOwnershipHelper(Object target, Class<?> parameterType) {
        try {
            Method method = Bukkit.class.getMethod("isOwnedByCurrentRegion", parameterType);
            Object result = method.invoke(null, target);
            return result == null || ((Boolean) result).booleanValue();
        } catch (NoSuchMethodException ignored) {
            try {
                Method method = Bukkit.getServer().getClass().getMethod("isOwnedByCurrentRegion", parameterType);
                Object result = method.invoke(Bukkit.getServer(), target);
                return result == null || ((Boolean) result).booleanValue();
            } catch (NoSuchMethodException ignoredAgain) {
                return true;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to query current region ownership", e);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to query current region ownership", e);
        }
    }

    private static final class BukkitCompatTask implements CompatTask {
        private final BukkitTask task;

        private BukkitCompatTask(BukkitTask task) {
            this.task = task;
        }

        @Override
        public void cancel() {
            try {
                task.cancel();
            } finally {
                TASKS.remove(this);
            }
        }

        @Override
        public boolean isCancelled() {
            return task.isCancelled();
        }
    }

    private static final class FoliaCompatTask implements CompatTask {
        private final Object task;

        private FoliaCompatTask(Object task) {
            this.task = task;
        }

        @Override
        public void cancel() {
            try {
                scheduledTaskCancel.invoke(task);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to cancel Folia scheduled task", e);
            } finally {
                TASKS.remove(this);
            }
        }

        @Override
        public boolean isCancelled() {
            try {
                return ((Boolean) scheduledTaskIsCancelled.invoke(task)).booleanValue();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to inspect Folia scheduled task state", e);
            }
        }
    }
}
