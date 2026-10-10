package com.harold.realisticsurvival.client;

import com.harold.realisticsurvival.Config;
import com.harold.realisticsurvival.ModRegistry;
import com.harold.realisticsurvival.RealisticSurvival;
import com.harold.realisticsurvival.survival.ThirstEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@Mod(value = RealisticSurvival.MODID, dist = Dist.CLIENT)
public class RealisticSurvivalClient {
    public RealisticSurvivalClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(RealisticSurvivalClient::registerRenderers);
        modBus.addListener(RealisticSurvivalClient::registerHud);
        NeoForge.EVENT_BUS.addListener(RealisticSurvivalClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(RealisticSurvivalClient::onRightClickEmpty);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistry.THROWN_SPEAR.get(), SpearRenderer::new);
    }

    private static void registerHud(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.FOOD_LEVEL, RealisticSurvival.id("thirst"), SurvivalHud::renderThirst);
        event.registerAbove(VanillaGuiLayers.ARMOR_LEVEL, RealisticSurvival.id("temperature"), SurvivalHud::renderTemperature);
    }

    /** Too thirsty or a broken leg: no sprinting. */
    private static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isCreative() || player.isSpectator() || !player.isSprinting()) return;
        boolean parched = Config.THIRST.getAsBoolean() && player.getData(ModRegistry.THIRST_HUD) <= 6;
        if (parched || player.hasEffect(ModRegistry.BROKEN_LEG)) player.setSprinting(false);
    }

    /** Sneak and right-click water with an empty hand to drink from it. */
    private static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        if (event.getHand() == InteractionHand.MAIN_HAND && ThirstEvents.canTryHandDrink(event.getEntity())) {
            ClientPacketDistributor.sendToServer(new ThirstEvents.DrinkFromHand());
        }
    }
}
