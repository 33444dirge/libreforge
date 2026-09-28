package audit.libreforge;

import com.willfp.libreforge.GlobalDispatcher;
import com.willfp.libreforge.triggers.*;
import com.willfp.libreforge.triggers.event.TriggerDispatchEvent;
import com.willfp.libreforge.triggers.impl.*;
import com.willfp.libreforge.integrations.paper.impl.TriggerTridentAttack;
import com.willfp.libreforge.triggers.placeholders.impl.TriggerPlaceholderHits;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.invoke.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Runs only in an isolated server. Synthetic UUID load is not a production MSPT benchmark. */
public final class HitsCleanupProbe extends JavaPlugin {
    private ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, Integer>> hits;
    private MethodHandle clearVictim;
    private final AtomicInteger assertions = new AtomicInteger(), failures = new AtomicInteger();
    private final AtomicInteger regionsDone = new AtomicInteger();
    private static final UUID PLAYER_A = new UUID(101, 1), PLAYER_B = new UUID(101, 2);
    private static final AtomicLong innerRemoves = new AtomicLong();
    private final boolean fixed = Boolean.getBoolean("hits.fixed");
    private static class CountingMap extends ConcurrentHashMap<UUID, Integer> {
        @Override public Integer remove(Object key) { innerRemoves.incrementAndGet(); return super.remove(key); }
    }
    @Override public void onEnable() {
        try {
            Field field = TriggerPlaceholderHits.class.getDeclaredField("hitsByEntity");
            field.setAccessible(true);
            @SuppressWarnings("unchecked") var map = (ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, Integer>>) field.get(null);
            hits = map;
            String method = fixed ? "clearVictim$common" : "clearEntity$common";
            clearVictim = MethodHandles.publicLookup().unreflect(TriggerPlaceholderHits.class.getMethod(method, UUID.class))
                    .bindTo(TriggerPlaceholderHits.INSTANCE);
            Bukkit.getGlobalRegionScheduler().runDelayed(this, t -> start(), 40L);
        } catch (Throwable e) { fail(e); shutdown(); }
    }
    private void check(String test, boolean pass) {
        assertions.incrementAndGet(); if (!pass) failures.incrementAndGet();
        getLogger().info("[HITS] CHECK " + test + " pass=" + pass);
    }
    private void fail(Throwable e) {
        failures.incrementAndGet(); getLogger().log(java.util.logging.Level.SEVERE, "[HITS] EXCEPTION", e);
    }
    private void shutdown() { Bukkit.getGlobalRegionScheduler().runDelayed(this, t -> Bukkit.shutdown(), 5L); }
    private void start() {
        World world = Bukkit.getWorlds().getFirst();
        Location location = new Location(world, 8, 100, 8);
        Bukkit.getRegionScheduler().run(this, location, t -> {
            try {
                ArmorStand stand = world.spawn(location, ArmorStand.class);
                stand.setGravity(false);
                functional(stand);
                stand.remove();
                removalEvents(world, location);
                startRegions(world);
            } catch (Throwable e) { fail(e); shutdown(); }
        });
    }
    private Player player(UUID uuid) {
        return (Player) Proxy.newProxyInstance(getClassLoader(), new Class<?>[]{Player.class}, (p,m,args) -> switch(m.getName()) {
            case "getUniqueId" -> uuid;
            case "getName" -> "AuditFixture";
            case "hashCode" -> uuid.hashCode();
            case "equals" -> p == args[0];
            case "toString" -> "AuditPlayer(" + uuid + ")";
            default -> throw new UnsupportedOperationException("Unexpected fake-player call: " + m.getName());
        });
    }
    private void hit(Trigger trigger, Player player, LivingEntity victim) {
        TriggerData data = new TriggerData(GlobalDispatcher.INSTANCE, player, victim, null, null, null, null, null, null, null, 1, 1);
        TriggerPlaceholderHits.INSTANCE.trackHits(new TriggerDispatchEvent(GlobalDispatcher.INSTANCE,
                new DispatchedTrigger(GlobalDispatcher.INSTANCE, trigger, data)));
    }
    private int count(UUID victim, UUID attacker) { var inner = hits.get(victim); return inner == null ? 0 : inner.getOrDefault(attacker, 0); }
    private void functional(ArmorStand stand) throws Throwable {
        hits.clear(); Player a = player(PLAYER_A), b = player(PLAYER_B); UUID v = stand.getUniqueId();
        double max = Objects.requireNonNull(stand.getAttribute(Attribute.MAX_HEALTH)).getValue();
        stand.setHealth(max);
        hit(TriggerMeleeAttack.INSTANCE,a,stand);
        check("full_health_first_hit_one", count(v,PLAYER_A)==1);
        stand.setHealth(max-1);
        hit(TriggerMeleeAttack.INSTANCE,a,stand); hit(TriggerBowAttack.INSTANCE,a,stand); hit(TriggerTridentAttack.INSTANCE,a,stand);
        check("melee_bow_trident_accumulate",count(v,PLAYER_A)==4);
        hit(TriggerMeleeAttack.INSTANCE,b,stand);
        check("independent_attackers",count(v,PLAYER_A)==4 && count(v,PLAYER_B)==1);
        stand.setHealth(max); hit(TriggerMeleeAttack.INSTANCE,b,stand);
        check("full_health_reset_other_attackers",count(v,PLAYER_A)==0 && count(v,PLAYER_B)==1);
        stand.setHealth(max-1); hit(TriggerMeleeAttack.INSTANCE,a,stand);
        Method getHits = TriggerPlaceholderHits.class.getDeclaredMethod("getHits",LivingEntity.class,Player.class);
        getHits.setAccessible(true);
        check("placeholder_count_lookup",((Integer)getHits.invoke(TriggerPlaceholderHits.INSTANCE,stand,a))==1);
        TriggerPlaceholderHits.INSTANCE.clearEntity$common(PLAYER_A);
        check("quit_removes_only_leaving_attacker",count(v,PLAYER_A)==0 && count(v,PLAYER_B)==1);
        hits.put(PLAYER_A,new ConcurrentHashMap<>(Map.of(PLAYER_B,5)));
        hits.get(v).put(PLAYER_A,2);
        TriggerPlaceholderHits.INSTANCE.clearEntity$common(PLAYER_A);
        check("quit_cleans_both_roles",!hits.containsKey(PLAYER_A) && count(v,PLAYER_A)==0 && count(v,PLAYER_B)==1);
        TriggerPlaceholderHits.INSTANCE.clearEntity$common(PLAYER_B);
        check("last_attacker_removes_empty_victim",hits.isEmpty());
        hit(TriggerMeleeAttack.INSTANCE,a,stand); clearVictim.invokeExact(v);
        check("victim_cleanup",!hits.containsKey(v));
        hit(TriggerMeleeAttack.INSTANCE,a,stand);
        check("new_hit_after_cleanup_restores_record",count(v,PLAYER_A)==1);
        TriggerPlaceholderHits.INSTANCE.clearAll$common();
        check("clear_all",hits.isEmpty());
    }
    private void seed(int n, boolean counted) {
        hits.clear();
        for(int i=0;i<n;i++) {
            ConcurrentHashMap<UUID,Integer> inner=counted?new CountingMap():new ConcurrentHashMap<>();
            inner.put(PLAYER_A,3); hits.put(new UUID(200,i),inner);
        }
    }
    private void removalEvents(World world, Location location) {
        seed(5000,true); innerRemoves.set(0);
        ArmorStand victim=world.spawn(location,ArmorStand.class); victim.setGravity(false);
        UUID v=victim.getUniqueId(); hits.put(v,new CountingMap()); hits.get(v).put(PLAYER_A,9);
        victim.remove();
        check("real_living_remove_cleans_victim",!hits.containsKey(v));
        long livingScans=innerRemoves.get();
        Item item=world.dropItem(location,new ItemStack(Material.STONE)); item.remove();
        long total=innerRemoves.get();
        check("unrelated_records_preserved",hits.size()==5000 && hits.values().stream().allMatch(m->m.get(PLAYER_A)==3));
        check("real_remove_scan_count",total==(fixed?0:10000));
        getLogger().info("[HITS] REAL_REMOVAL victims=5000 living_inner_removes="+livingScans+" item_inner_removes="+(total-livingScans));
        hits.clear();
    }
    private void startRegions(World world) {
        seed(1000,true); innerRemoves.set(0);
        for(int r=0;r<4;r++) {
            Location location=new Location(world,8+r*1024,100,8);
            world.getChunkAtAsync(location).whenComplete((chunk,error)-> {
                if(error!=null){fail(error);regionDone();return;}
                Bukkit.getRegionScheduler().run(this,location,t->{
                    getLogger().info("[HITS] REGION location="+location.getBlockX()+",8 owner="+Bukkit.isOwnedByCurrentRegion(location)+" thread="+Thread.currentThread());
                    try {for(int i=0;i<25;i++){ArmorStand e=world.spawn(location,ArmorStand.class);e.remove();}}
                    catch(Throwable e){fail(e);}finally{regionDone();}
                });
            });
        }
    }
    private void regionDone() {
        if(regionsDone.incrementAndGet()!=4)return;
        check("four_region_locations_preserve_records",hits.size()==1000);
        long scans=innerRemoves.get();
        check("four_region_locations_scan_count",scans==(fixed?0:100000));
        getLogger().info("[HITS] REGION_BATCH locations=4 removed=100 inner_removes="+scans);
        // Only UUID/count data in the following worker; no live Bukkit state is accessed.
        Bukkit.getAsyncScheduler().runNow(this,t->{try{benchmark();}catch(Throwable e){fail(e);}finally{finish();}});
    }
    private void benchmark() throws Throwable {
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true); long tid=Thread.currentThread().threadId();
        UUID unrelated=new UUID(300,1);
        for(int n:new int[]{1000,10000}) {
            seed(n,false);
            for(int i=0;i<1000;i++)clearVictim.invokeExact(unrelated);
            double[] ns=new double[5], alloc=new double[5]; int ops=1000;
            for(int round=0;round<5;round++) {
                long bytes=bean.getThreadAllocatedBytes(tid),start=System.nanoTime();
                for(int i=0;i<ops;i++)clearVictim.invokeExact(unrelated);
                ns[round]=(System.nanoTime()-start)/(double)ops;
                alloc[round]=(bean.getThreadAllocatedBytes(tid)-bytes)/(double)ops;
            }
            Arrays.sort(ns);Arrays.sort(alloc);
            check("benchmark_retains_unrelated_"+n,hits.size()==n);
            getLogger().info(String.format(Locale.ROOT,"[HITS] BENCH victims=%d ops_per_round=%d rounds=5 median_ns=%.3f median_bytes=%.3f",n,ops,ns[2],alloc[2]));
        }
        hits.clear();
        var pool=Executors.newFixedThreadPool(8); List<Future<?>> futures=new ArrayList<>();
        try {
            for(int t=0;t<8;t++){final int id=t;futures.add(pool.submit(()->{
                for(int i=0;i<1000;i++){UUID v=new UUID(400+id,i);hits.put(v,new ConcurrentHashMap<>(Map.of(PLAYER_A,1)));try{clearVictim.invokeExact(v);}catch(Throwable e){throw new RuntimeException(e);}}
            }));}
            for(Future<?> f:futures)f.get(30,TimeUnit.SECONDS);
            check("eight_worker_uuid_cleanup_8000",hits.isEmpty());
        } finally {pool.shutdownNow();}
        seed(100,false);TriggerPlaceholderHits.INSTANCE.clearAll$common();
        check("final_clear_all_zero",hits.isEmpty());
    }
    private void finish() {
        TriggerPlaceholderHits.INSTANCE.clearAll$common();
        getLogger().info("[HITS] SUMMARY fixed="+fixed+" assertions="+assertions.get()+" failures="+failures.get()+" retained="+hits.size());
        shutdown();
    }
}
