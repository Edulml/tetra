package se.mickelus.tetra.items.modular.impl.shield;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.BannerItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import se.mickelus.tetra.TetraMod;
import se.mickelus.tetra.advancements.ImprovementCraftCriterion;
import se.mickelus.tetra.capabilities.Capability;
import se.mickelus.tetra.gui.GuiTextures;
import se.mickelus.tetra.items.modular.ItemModular;
import se.mickelus.tetra.module.ItemModuleMajor;
import se.mickelus.tetra.module.data.GlyphData;
import se.mickelus.tetra.module.schema.OutcomePreview;
import se.mickelus.tetra.module.schema.SchemaType;
import se.mickelus.tetra.module.schema.UpgradeSchema;
import se.mickelus.tetra.util.CastOptional;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

public class ApplyBannerSchema implements UpgradeSchema {
    private static final String localizationPrefix = TetraMod.MOD_ID + "/schema/";
    private static final String key = "shield/plate/banner";

    private static final String nameSuffix = ".name";
    private static final String descriptionSuffix = ".description";
    private static final String slotSuffix = ".slot1";

    private GlyphData glyph = new GlyphData(GuiTextures.glyphs, 96, 240);

    public ApplyBannerSchema() {}

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public String getName() {
        return I18n.format(localizationPrefix + key + nameSuffix);
    }

    @Override
    public String getDescription(ItemStack itemStack) {
        return I18n.format(localizationPrefix + key + descriptionSuffix);
    }

    @Override
    public int getNumMaterialSlots() {
        return 1;
    }

    @Override
    public String getSlotName(final ItemStack itemStack, final int index) {
        return I18n.format(localizationPrefix + key + slotSuffix);
    }

    @Override
    public ItemStack[] getSlotPlaceholders(ItemStack itemStack, int index) {
        return new ItemStack[] { Items.WHITE_BANNER.getDefaultInstance() };
    }

    @Override
    public int getRequiredQuantity(ItemStack itemStack, int index, ItemStack materialStack) {
        return 1;
    }

    @Override
    public boolean acceptsMaterial(ItemStack itemStack, String itemSlot, int index, ItemStack materialStack) {
        return materialStack.getItem() instanceof BannerItem;
    }

    @Override
    public boolean isMaterialsValid(ItemStack itemStack, String itemSlot, ItemStack[] materials) {
        return acceptsMaterial(itemStack, itemSlot, 0, materials[0]);
    }

    @Override
    public boolean isApplicableForItem(ItemStack itemStack) {
        return itemStack.getItem() instanceof ModularShieldItem;
    }

    @Override
    public boolean isApplicableForSlot(String slot, ItemStack targetStack) {
        return ModularShieldItem.plateKey.equals(slot);
    }

    @Override
    public boolean canApplyUpgrade(PlayerEntity player, ItemStack itemStack, ItemStack[] materials, String slot, int[] availableCapabilities) {
        return isMaterialsValid(itemStack, slot, materials);
    }

    @Override
    public boolean isIntegrityViolation(PlayerEntity player, ItemStack itemStack, ItemStack[] materials, String slot) {
        return false;
    }

    @Override
    public ItemStack applyUpgrade(ItemStack itemStack, ItemStack[] materials, boolean consumeMaterials, String slot, PlayerEntity player) {
        ItemStack upgradedStack = itemStack.copy();

        ItemStack bannerStack = materials[0];

        if (isMaterialsValid(itemStack, slot, materials)) {

            CastOptional.cast(itemStack.getItem(), ItemModular.class)
                    .map(item -> item.getModuleFromSlot(itemStack, slot))
                    .flatMap(module -> CastOptional.cast(module, ItemModuleMajor.class))
                    .ifPresent(module -> {
                        if (module.acceptsImprovementLevel(ModularShieldItem.bannerImprovementKey, 0)) {
                            module.addImprovement(upgradedStack, ModularShieldItem.bannerImprovementKey, 0);

                            CompoundNBT bannerTag = Optional.ofNullable(bannerStack.getChildTag("BlockEntityTag"))
                                    .map(CompoundNBT::copy)
                                    .orElse(new CompoundNBT());

                            bannerTag.putInt("Base", ((BannerItem) bannerStack.getItem()).getColor().getId());
                            upgradedStack.setTagInfo("BlockEntityTag", bannerTag.copy());

                            if (consumeMaterials) {
                                materials[0].shrink(1);
                            }

                            if (consumeMaterials && player instanceof ServerPlayerEntity) {
                                ImprovementCraftCriterion.trigger((ServerPlayerEntity) player, itemStack, upgradedStack, getKey(), slot,
                                        ModularShieldItem.bannerImprovementKey, 0, null, -1);
                            }
                        }
                    });
        }


        return upgradedStack;
    }

    @Override
    public boolean checkCapabilities(ItemStack targetStack, ItemStack[] materials, int[] availableCapabilities) {
        return true;
    }

    @Override
    public Collection<Capability> getRequiredCapabilities(ItemStack targetStack, ItemStack[] materials) {
        return Collections.emptyList();
    }

    @Override
    public int getRequiredCapabilityLevel(ItemStack targetStack, ItemStack[] materials, Capability capability) {
        return 0;
    }

    @Override
    public SchemaType getType() {
        return SchemaType.improvement;
    }

    @Override
    public GlyphData getGlyph() {
        return glyph;
    }

    @Override
    public OutcomePreview[] getPreviews(ItemStack targetStack, String slot) {
        return new OutcomePreview[0];
    }
}
