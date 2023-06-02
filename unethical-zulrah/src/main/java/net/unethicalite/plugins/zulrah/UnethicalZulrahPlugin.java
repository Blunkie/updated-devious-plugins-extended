package net.unethicalite.plugins.zulrah;

import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.PluginDescriptor;
import net.unethicalite.api.entities.NPCs;
import net.unethicalite.api.entities.Players;
import net.unethicalite.api.plugins.Plugins;
import net.unethicalite.api.plugins.Task;
import net.unethicalite.api.plugins.TaskPlugin;
import net.unethicalite.api.widgets.Dialog;
import net.unethicalite.api.widgets.Widgets;
import net.unethicalite.client.Static;
import net.unethicalite.plugins.zulrah.data.Constants;
import net.unethicalite.plugins.zulrah.data.GearSetup;
import net.unethicalite.plugins.zulrah.data.phases.ZulrahCycle;
import net.unethicalite.plugins.zulrah.data.phases.ZulrahNode;
import net.unethicalite.plugins.zulrah.data.phases.ZulrahTree;
import net.unethicalite.plugins.zulrah.data.phases.ZulrahType;
import net.unethicalite.plugins.zulrah.framework.ZulrahTask;
import net.unethicalite.plugins.zulrah.tasks.AttackZulrah;
import net.unethicalite.plugins.zulrah.tasks.AvoidAttack;
import net.unethicalite.plugins.zulrah.tasks.DrinkPotions;
import net.unethicalite.plugins.zulrah.tasks.Eating;
import net.unethicalite.plugins.zulrah.tasks.EnterZulrah;
import net.unethicalite.plugins.zulrah.tasks.JadPhase;
import net.unethicalite.plugins.zulrah.tasks.RecoilRing;
import net.unethicalite.plugins.zulrah.tasks.SwitchGear;
import net.unethicalite.plugins.zulrah.tasks.TogglePrayers;
import net.unethicalite.plugins.zulrah.tasks.Traverse;
import org.pf4j.Extension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Extension
@PluginDescriptor(name = "Unethical Zulrah", enabledByDefault = false)
@Slf4j
public class UnethicalZulrahPlugin extends TaskPlugin {
    public ZulrahNode node;
    public WorldPoint origin;
    public String rotationName = "None";
    private ZulrahTree tree;
    private ZulrahNode firstPhase;
    private ZulrahNode secondPhase;
    private ZulrahNode thirdPhase;
    private ZulrahNode fourthPhase;
    private int zulrahAttacks = 0;

    private final Task[] tasks = new Task[]
            {
                    new RecoilRing(),
                    new TogglePrayers(),
                    new Eating(),
                    new DrinkPotions(),
                    new Traverse(),
                    new SwitchGear(),
                    new JadPhase(),
                    new AttackZulrah(),
                    new AvoidAttack(),
                    new EnterZulrah()
            };

    @Inject
    private UnethicalZulrahConfig config;

    @Override
    protected void startUp() {
        ZulrahNode root = new ZulrahNode(null, ZulrahCycle.INITIAL);
        thirdPhase = root.add(ZulrahCycle.GREEN_EAST_NE);

        thirdPhase.add(ZulrahCycle.MAGMA_CENTER_NW)
                .add(ZulrahCycle.TANZ_WEST_W)
                .add(ZulrahCycle.GREEN_SOUTH_W)
                .add(ZulrahCycle.TANZ_EAST_E)
                .add(ZulrahCycle.GREEN_CENTER_W)
                .add(ZulrahCycle.GREEN_WEST_W)
                .add(ZulrahCycle.TANZ_CENTER_E)
                .add(ZulrahCycle.JAD_PHASE_E)
                .add(ZulrahCycle.TANZ_CENTER_NE);

        fourthPhase = root.add(ZulrahCycle.TANZ_EAST_NE);
        fourthPhase.add(ZulrahCycle.GREEN_SOUTH_W)
                .add(ZulrahCycle.TANZ_WEST_W)
                .add(ZulrahCycle.MAGMA_CENTER_E)
                .add(ZulrahCycle.GREEN_EAST_E)
                .add(ZulrahCycle.GREEN_SOUTH_E_W)
                .add(ZulrahCycle.GREEN_SOUTH_W)
                .add(ZulrahCycle.TANZ_WEST_W)
                .add(ZulrahCycle.GREEN_CENTER_E)
                .add(ZulrahCycle.TANZ_CENTER_E)
                .add(ZulrahCycle.JAD_PHASE_E)
                .add(ZulrahCycle.TANZ_CENTER_NE);

        firstPhase = root.add(ZulrahCycle.MAGMA_CENTER_NE)
                .add(ZulrahCycle.TANZ_CENTER_E);

        firstPhase.add(ZulrahCycle.GREEN_SOUTH_W)
                .add(ZulrahCycle.MAGMA_CENTER_W)
                .add(ZulrahCycle.TANZ_WEST_W)
                .add(ZulrahCycle.GREEN_SOUTH_E)
                .add(ZulrahCycle.TANZ_SOUTH_E_CW)
                .add(ZulrahCycle.TANZ_SOUTH_CW)
                .add(ZulrahCycle.JAD_PHASE_W)
                .add(ZulrahCycle.MAGMA_CENTER_NE);

        secondPhase = firstPhase.add(ZulrahCycle.GREEN_WEST_W)
                .add(ZulrahCycle.TANZ_SOUTH_W)
                .add(ZulrahCycle.MAGMA_CENTER_W)
                .add(ZulrahCycle.GREEN_EAST_CE)
                .add(ZulrahCycle.TANZ_SOUTH_CW)
                .add(ZulrahCycle.JAD_PHASE_W)
                .add(ZulrahCycle.MAGMA_CENTER_NE);

        tree = new ZulrahTree(root);

        List<String> rangeGearNames = Arrays.stream(config.rangeGearNames().split(","))
                .collect(Collectors.toList());
        List<String> mageGearNames = Arrays.stream(config.mageGearNames().split(","))
                .collect(Collectors.toList());

        ZulrahType.setMagePhaseGear(new GearSetup(rangeGearNames));
        ZulrahType.setRangedMeleePhaseGear(new GearSetup(mageGearNames));
    }

    public Task[] getTasks() {
        return tasks;
    }

    private void refresh() {
        if (!atZulrah()) {
            node = null;
            origin = null;
            rotationName = "None";
            return;
        }

        // on enter
        Widget cont = Widgets.get(WidgetInfo.DIALOG_NOTIFICATION_CONTINUE);
        if (cont != null && cont.getText().startsWith("The priestess rows you to Zulrah's") && Dialog.canContinue()) {
            origin = Players.getLocal().getWorldLocation();
            node = tree.getRoot();
            return;
        }

        NPC zulrah = NPCs.getNearest(Constants.ZULRAH_NAME);
        if (zulrah != null) {
            // go back to root if we're not on a node
            if (node == null) {
                log.debug("Node is null");
                rotationName = "None";
                node = tree.getRoot();
                origin = Players.getLocal().getWorldLocation();
                return;
            }

            if (node.equals(firstPhase)) rotationName = "First rotation";
            if (node.equals(secondPhase)) rotationName = "Second rotation";
            if (node.equals(thirdPhase)) rotationName = "Third rotation";
            if (node.equals(fourthPhase)) rotationName = "Fourth rotation";

            List<ZulrahNode> children = node.getChildren();

            if (rotationName.equals("First rotation") && node.getZulrahCycle() == ZulrahCycle.TANZ_SOUTH_E_CW && zulrahAttacks >= 3) {
                node = children.get(0);
            }

            if (rotationName.equals("Fourth rotation") && node.getZulrahCycle() == ZulrahCycle.GREEN_SOUTH_E_W && zulrahAttacks >= 6) {
                node = children.get(0);
            }

            // on despawn, set new node
            if (zulrah.getAnimation() == Constants.DISAPPEAR_ANIMATION
                    && node.getZulrahCycle().getZulrahType().id() == zulrah.getId()
                    && node.getZulrahCycle().getZulrahPosition(origin).equals(zulrah.getWorldLocation())) {
                log.debug("Zulrah despawned, cycling node");
                zulrahAttacks = 0;

                if (children.size() == 0) {
                    node = tree.getRoot();
                    return;
                }

                node = children.get(0);
                return;
            }

            // if we're on the wrong node, go up 1 node and start traversal
            if (node.getZulrahCycle().getZulrahType().id() != zulrah.getId()
                    || !node.getZulrahCycle().getZulrahPosition(origin).equals(zulrah.getWorldLocation())) {
                log.debug("We are on the wrong node {} {}", node.getZulrahCycle().getZulrahPosition(origin), zulrah.getWorldLocation());
                ZulrahNode parent = node.getParent();
                zulrahAttacks = 0;

                if (parent == null) {
                    return;
                }

                // check children until we found the correct rotation
                List<ZulrahNode> childs = new ArrayList<>(parent.getChildren());
                for (ZulrahNode node : childs) {
                    if (node.getZulrahCycle().getZulrahPosition(origin).equals(zulrah.getWorldLocation())
                            && node.getZulrahCycle().getZulrahType().id() == zulrah.getId()) {
                        log.debug("Found correct node");
                        this.node = node;
                        return;
                    }
                }
            }
        }
    }

    private void updateCycle(ZulrahCycle zulrahCycle) {
        for (Task task : getTasks()) {
            if (task instanceof ZulrahTask) {
                ((ZulrahTask) task).setZulrahCycle(zulrahCycle);
            }
        }
    }

    private void updateOrigin(WorldPoint origin) {
        for (Task task : getTasks()) {
            if (task instanceof ZulrahTask) {
                ((ZulrahTask) task).setOrigin(origin);
            }
        }
    }

    public static boolean atZulrah() {
        return Static.getClient().isInInstancedRegion();
    }

    @Provides
    UnethicalZulrahConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(UnethicalZulrahConfig.class);
    }

    @Subscribe
    private void onActorDeath(ActorDeath event) {
        if (event.getActor() == Players.getLocal()) {
            Plugins.stopPlugin(this);
        }
    }

    @Subscribe
    private void onGameTick(GameTick e) {
        refresh();

        if (node != null) {
            updateCycle(node.getZulrahCycle());
            updateOrigin(origin);
        } else {
            updateCycle(null);
            updateOrigin(null);
        }
    }
}

