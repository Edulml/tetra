package se.mickelus.tetra.items;

import com.mojang.realmsclient.gui.ChatFormatting;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.enchantment.EnchantmentDurability;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.lwjgl.input.Keyboard;
import se.mickelus.tetra.NBTHelper;
import se.mickelus.tetra.capabilities.Capability;
import se.mickelus.tetra.capabilities.ICapabilityProvider;
import se.mickelus.tetra.module.*;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.google.common.collect.ImmutableList;
import se.mickelus.tetra.module.data.CapabilityData;
import se.mickelus.tetra.module.data.ImprovementData;
import se.mickelus.tetra.module.data.SynergyData;
import se.mickelus.tetra.module.schema.Material;

public abstract class ItemModular extends TetraItem implements IItemModular, ICapabilityProvider {

    protected static final String repairCountKey = "repairCount";

    protected static final String cooledStrengthKey = "cooledStrength";

    protected String[] majorModuleKeys;
    protected String[] minorModuleKeys;

    protected String[] requiredModules = new String[0];

    protected int baseDurability = 0;
    protected int baseIntegrity = 0;

    protected SynergyData[] synergies = new SynergyData[0];

    @Override
    public int getMaxDamage(ItemStack stack) {
        return getAllModules(stack).stream()
                .map(itemModule -> itemModule.getDurability(stack))
                .reduce(0, Integer::sum) + baseDurability;
    }

    public static int getIntegrityGain(ItemStack itemStack) {
        if (itemStack.getItem() instanceof ItemModular) {
            return ((ItemModular) itemStack.getItem()).getAllModules(itemStack).stream()
                    .map(module -> module.getIntegrityGain(itemStack))
                    .reduce(0, Integer::sum);
        }
        return 0;
    }

    public static int getIntegrityCost(ItemStack itemStack) {
        if (itemStack.getItem() instanceof ItemModular) {
            return ((ItemModular) itemStack.getItem()).getAllModules(itemStack).stream()
                    .map(module -> module.getIntegrityCost(itemStack))
                    .reduce(0, Integer::sum);
        }
        return 0;
    }

    protected Collection<ItemModule> getAllModules(ItemStack stack) {
        NBTTagCompound stackTag = NBTHelper.getTag(stack);

        if (stackTag != null) {
            return Stream.concat(Arrays.stream(majorModuleKeys),Arrays.stream(minorModuleKeys))
                    .map(stackTag::getString)
                    .map(ItemUpgradeRegistry.instance::getModule)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    @Override
    public ItemModuleMajor[] getMajorModules(ItemStack itemStack) {
        ItemModuleMajor[] modules = new ItemModuleMajor[majorModuleKeys.length];
        NBTTagCompound stackTag = NBTHelper.getTag(itemStack);

        for (int i = 0; i < majorModuleKeys.length; i++) {
            String moduleName = stackTag.getString(majorModuleKeys[i]);
            ItemModule module = ItemUpgradeRegistry.instance.getModule(moduleName);
            if (module instanceof ItemModuleMajor) {
                modules[i] = (ItemModuleMajor) module;
            }
        }

        return modules;
    }

    @Override
    public ItemModule[] getMinorModules(ItemStack itemStack) {
        ItemModule[] modules = new ItemModule[minorModuleKeys.length];
        NBTTagCompound stackTag = NBTHelper.getTag(itemStack);

        for (int i = 0; i < minorModuleKeys.length; i++) {
            String moduleName = stackTag.getString(minorModuleKeys[i]);
            ItemModule module = ItemUpgradeRegistry.instance.getModule(moduleName);
            modules[i] = module;
        }

        return modules;
    }

    @Override
    public boolean isModuleRequired(String moduleSlot) {
        return ArrayUtils.contains(requiredModules, moduleSlot);
    }

    @Override
    public int getNumMajorModules() {
        return majorModuleKeys.length;
    }

    @Override
    public String[] getMajorModuleKeys() {
        return majorModuleKeys;
    }

    @Override
    public String[] getMajorModuleNames() {
        return Arrays.stream(majorModuleKeys)
                .map(key -> I18n.format(key))
                .toArray(String[]::new);
    }

    @Override
    public int getNumMinorModules() {
        return minorModuleKeys.length;
    }

    @Override
    public String[] getMinorModuleKeys() {
        return minorModuleKeys;
    }

    @Override
    public String[] getMinorModuleNames() {
        return Arrays.stream(minorModuleKeys)
                .map(key -> I18n.format(key))
                .toArray(String[]::new);
    }

    @Override
    public ImmutableList<ResourceLocation> getTextures(ItemStack itemStack) {

        return getAllModules(itemStack).stream()
                .sorted(Comparator.comparing(ItemModule::getRenderLayer))
                .flatMap(itemModule -> Arrays.stream(itemModule.getTextures(itemStack)))
                .collect(Collectors.collectingAndThen(Collectors.toList(), ImmutableList::copyOf));
    }

    public boolean hasModule(ItemStack itemStack, ItemModule module) {
        return getAllModules(itemStack).stream()
            .anyMatch(module::equals);
    }

    public ItemModule getModuleFromSlot(ItemStack itemStack, String slot) {
        return ItemUpgradeRegistry.instance.getModule(NBTHelper.getTag(itemStack).getString(slot));
    }

    public void applyDamage(int amount, ItemStack itemStack, EntityLivingBase responsibleEntity) {
        itemStack.damageItem(getReducedDamage(amount, itemStack, responsibleEntity), responsibleEntity);
    }

    @Override
    public void setDamage(ItemStack itemStack, int damage) {
        super.setDamage(itemStack, Math.min(itemStack.getMaxDamage(), damage));
    }

    private int getReducedDamage(int amount, ItemStack itemStack, EntityLivingBase responsibleEntity) {
        if (amount > 0) {
            int level = getEffectLevel(itemStack, ItemEffect.unbreaking);
            int reduction = 0;

            for (int i = 0; i < amount; i++) {
                if (EnchantmentDurability.negateDamage(itemStack, level, responsibleEntity.world.rand)) {
                    reduction++;
                }
            }
            return amount - reduction;
        }
        return amount;
    }

    public boolean isBroken(ItemStack itemStack) {
        return itemStack.getMaxDamage() != 0 && itemStack.getItemDamage() >= itemStack.getMaxDamage();
    }

    @Override
    public void addInformation(ItemStack itemStack, @Nullable World playerIn, List<String> tooltip, ITooltipFlag advanced) {
        if (isBroken(itemStack)) {
            tooltip.add(TextFormatting.DARK_RED.toString() + TextFormatting.ITALIC + I18n.format("item.modular.broken"));
        }

        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            Arrays.stream(getMajorModules(itemStack))
                    .filter(Objects::nonNull)
                    .forEach(module -> {
                            tooltip.add("\u00BB " + module.getName(itemStack));
                            Arrays.stream(module.getImprovements(itemStack))
                                    .map(improvement -> String.format(" %s- %s %s",
                                            ChatFormatting.DARK_GRAY,
                                            I18n.format(improvement.key + ".name"),
                                            improvement.level > 0 ? I18n.format("enchantment.level." + improvement.level) : ""))
                                    .forEach(tooltip::add);
                    });
            Arrays.stream(getMinorModules(itemStack))
                    .filter(Objects::nonNull)
                    .map(module -> "* " + module.getName(itemStack))
                    .forEach(tooltip::add);
        } else {
            Arrays.stream(getMajorModules(itemStack))
                    .filter(Objects::nonNull)
                    .flatMap(module -> Arrays.stream(module.getImprovements(itemStack)))
                    .filter(improvement -> improvement.enchantment)
                    .map(improvement -> I18n.format(improvement.key + ".name") + " "
                            + I18n.format("enchantment.level." + improvement.level))
                    .forEach(tooltip::add);

        }
    }

    /**
     * Returns the durability and material multiplier of the next repair attempt.
     * @param repairCount The amount of repairs done so far, used as seed
     * @return A value between 0.3 and 1
     */
    private float getRepairMultiplier(int repairCount) {
        // the seed has to change a significant amount for there to be any noticable change, 500 seems to be a decent value
        return Math.max(0.3f, new Random(repairCount*500).nextFloat());
    }

    /**
     * Returns an optional with the module that will be repaired in next repair attempt, the optional is empty if
     * there are no repairable modules in this item.
     * @param itemStack The itemstack for the modular item
     * @return An optional with the module that will be repaired in next repair attempt
     */
    private Optional<ItemModule> getRepairModule(ItemStack itemStack) {
        List<ItemModule> modules = getAllModules(itemStack).stream()
                .filter(itemModule -> itemModule.getRepairMaterial(itemStack) != null)
                .collect(Collectors.toList());

        if (modules.size() > 0) {
            int repairCount = getRepairCount(itemStack);
            return Optional.of(modules.get(repairCount % modules.size()));
        }
        return Optional.empty();
    }

    /**
     * Returns an itemstack with the material required for the next repair attempt. Rotates between materials required
     * for different modules
     * @param itemStack The itemstack for the modular item
     * @return The material definition for the material required for the next repair attempt
     */
    public Material getRepairMaterial(ItemStack itemStack) {
        return getRepairModule(itemStack)
                .map(module -> {
                    Material material = module.getRepairMaterial(itemStack);
                    int repairCount = getRepairCount(itemStack);

                    return material;
                })
                .orElse(null);
    }

    /**
     * Returns the required size of the repair material itemstack for the next repair attempt.
     * @param itemStack The itemstack for the modular item
     * @return
     */
    public int getRepairMaterialCount(ItemStack itemStack) {
        return getRepairModule(itemStack)
                .map(module -> {
                    Material material = module.getRepairMaterial(itemStack);
                    int repairCount = getRepairCount(itemStack);

                    if (material.count > 1) {
                        return Math.max(1, (int)(getRepairMultiplier(repairCount) * material.count));
                    }

                    return material.count;
                })
                .orElse(0);
    }

    /**
     * Returns the amount of durability restored by the next repair attempt.
     * @param itemStack The itemstack for the modular item
     * @return
     */
    public int getRepairAmount(ItemStack itemStack) {
        return getRepairModule(itemStack)
                .map(module -> {
                    int repairCount = getRepairCount(itemStack);
                    return (int) (module.getRepairAmount(itemStack) * getRepairMultiplier(repairCount));
                })
                .orElse(0);
    }

    public Collection<Capability> getRepairRequiredCapabilities(ItemStack itemStack) {
        return getRepairModule(itemStack)
                .map(module -> module.getRepairRequiredCapabilities(itemStack))
                .orElse(Collections.emptyList());
    }

    public int getRepairRequiredCapabilityLevel(ItemStack itemStack, Capability capability) {
        return getRepairModule(itemStack)
                .filter(module -> module.getRepairRequiredCapabilities(itemStack).contains(capability))
                .map(module -> module.getRepairRequiredCapabilityLevel(itemStack, capability))
                .map(level -> Math.max(1, level))
                .orElse(0);
    }

    /**
     * Returns the number of times this item has been repaired.
     * @param itemStack The itemstack for the modular item
     * @return
     */
    private int getRepairCount(ItemStack itemStack) {
        return NBTHelper.getTag(itemStack).getInteger(repairCountKey);
    }

    private void incrementRepairCount(ItemStack itemStack) {
        NBTTagCompound tag = NBTHelper.getTag(itemStack);
        tag.setInteger(repairCountKey, tag.getInteger(repairCountKey) + 1);
    }

    public void repair(ItemStack itemStack) {
        setDamage(itemStack, getDamage(itemStack) - getRepairAmount(itemStack));

        incrementRepairCount(itemStack);
    }

    public int getCapabilityLevel(ItemStack itemStack, String capability) {
        if (EnumUtils.isValidEnum(Capability.class, capability)) {
            return getCapabilityLevel(itemStack, Capability.valueOf(capability));
        }
        return -1;
    }

    @Override
    public int getCapabilityLevel(ItemStack itemStack, Capability capability) {
        int base = getAllModules(itemStack).stream()
                .map(module -> module.getCapabilityLevel(itemStack, capability))
                .max(Integer::compare)
                .orElse(-1);

        int synergyBonus = Arrays.stream(getSynergyData(itemStack))
                .map(synergyData -> synergyData.capabilities)
                .mapToInt(capabilityData -> capabilityData.getLevel(capability))
                .sum();

        return base + synergyBonus;
    }

    /**
     * Apply special effects and possibly consume required resources after this item has been used to craft or upgrade
     * another item. This is called once for each capability used by the craft, which this item provide.
     * @param providerStack The providing stack, the itemstack for this item
     * @param targetStack The itemstack which is being upgraded/crafted/altered in some way
     * @param player The player performing the actions
     * @param capability The capability used
     * @param capabilityLevel The level of the used capability
     * @param consumeResources
     */
    public ItemStack onCraftConsumeCapability(ItemStack providerStack, ItemStack targetStack, EntityPlayer player,
            Capability capability, int capabilityLevel, boolean consumeResources) {
        ItemStack result = targetStack.copy();

        return result;
    }

    /**
     * Apply special effects and possibly consume required resources after this item has been used to perform a
     * workbench action.
     * @param providerStack The providing stack, the itemstack for this item
     * @param targetStack The itemstack which the action is performed upon
     * @param player The player performing the action
     * @param capability The capability used
     * @param capabilityLevel The level of the used capability
     * @param consumeResources
     */
    public ItemStack onActionConsumeCapability(ItemStack providerStack, ItemStack targetStack, EntityPlayer player,
            Capability capability, int capabilityLevel, boolean consumeResources) {
        ItemStack result = targetStack.copy();

        return result;
    }

    public int getEffectLevel(ItemStack itemStack, ItemEffect effect) {
        return getAllModules(itemStack).stream()
                .mapToInt(module -> module.getEffectLevel(itemStack, effect))
                .sum();
    }

    public double getEffectEfficiency(ItemStack itemStack, ItemEffect effect) {
        return getAllModules(itemStack).stream()
                .mapToDouble(module -> module.getEffectEfficiency(itemStack, effect))
                .sum();
    }

    public Collection<ItemEffect> getEffects(ItemStack itemStack) {
        return getAllModules(itemStack).stream()
                .flatMap(module -> ((Collection<ItemEffect>)module.getEffects(itemStack)).stream())
                .distinct()
                .collect(Collectors.toSet());

    }

    @Override
    public boolean hasEffect(ItemStack itemStack) {
        return Arrays.stream(getImprovements(itemStack))
                .anyMatch(improvement -> improvement.enchantment);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged;
    }

    public float getCapabilityEfficiency(ItemStack itemStack, String capability) {
        if (EnumUtils.isValidEnum(Capability.class, capability)) {
            return getCapabilityEfficiency(itemStack, Capability.valueOf(capability));
        }
        return -1;
    }

    @Override
    public float getCapabilityEfficiency(ItemStack itemStack, Capability capability) {
        int highestLevel = getAllModules(itemStack).stream()
                .map(module -> module.getCapabilityLevel(itemStack, capability))
                .max(Integer::compare)
                .orElse(-1);

        float efficiency = getAllModules(itemStack).stream()
                .filter(module -> module.getCapabilityLevel(itemStack, capability) >= highestLevel)
                .map(module -> module.getCapabilityEfficiency(itemStack, capability))
                .max(Float::compare)
                .orElse(1f);

        return efficiency + (float) Arrays.stream(getSynergyData(itemStack))
                .map(synergyData -> synergyData.capabilities)
                .mapToDouble(capabilityData -> capabilityData.getEfficiency(capability))
                .sum();
    }

    @Override
    public Collection<Capability> getCapabilities(ItemStack itemStack) {
        return getAllModules(itemStack).stream()
                .flatMap(module -> ((Collection<Capability>)module.getCapabilities(itemStack)).stream())
                .collect(Collectors.toSet());
    }

    public ImprovementData[] getImprovements(ItemStack itemStack) {
        return Arrays.stream(getMajorModules(itemStack))
                .filter(Objects::nonNull)
                .flatMap(module -> Arrays.stream(module.getImprovements(itemStack)))
                .toArray(ImprovementData[]::new);
    }

    protected String getDisplayNamePrefixes(ItemStack itemStack) {
        return Stream.concat(
                Arrays.stream(getImprovements(itemStack))
                        .map(improvement -> improvement.key + ".prefix")
                        .filter(I18n::hasKey)
                        .map(I18n::format),
                getAllModules(itemStack).stream()
                        .sorted(Comparator.comparing(module -> module.getItemPrefixPriority(itemStack)))
                        .map(module -> module.getItemPrefix(itemStack))
                        .filter(Objects::nonNull)
        )
                .limit(2)
                .reduce("", (result, prefix) -> result + prefix + " ");
    }

    @Override
    public String getItemStackDisplayName(ItemStack itemStack) {
        // todo: since getItemStackDisplayName is called on the server we cannot use the new I18n service
        if (FMLCommonHandler.instance().getEffectiveSide().equals(Side.SERVER)) {
            return "";
        }
        String name = Arrays.stream(getSynergyData(itemStack))
                .map(synergyData -> synergyData.name)
                .filter(Objects::nonNull)
                .filter(I18n::hasKey)
                .map(I18n::format)
                .findFirst()
                .orElse(null);

        if (name == null) {
            name = getAllModules(itemStack).stream()
                    .sorted(Comparator.comparing(module -> module.getItemNamePriority(itemStack)))
                    .map(module -> module.getItemName(itemStack))
                    .filter(Objects::nonNull)
                    .findFirst().orElse("");
        }

        String prefixes = getDisplayNamePrefixes(itemStack);
        return WordUtils.capitalize(prefixes + name);
    }

    protected SynergyData[] getSynergyData(ItemStack itemStack) {
        if (synergies.length > 0) {
            ArrayList<SynergyData> result = new ArrayList<>();
            String[] moduleKeys = getAllModules(itemStack).stream()
                    .map(ItemModule::getUnlocalizedName)
                    .sorted()
                    .toArray(String[]::new);
            String[] variantKeys = getAllModules(itemStack).stream()
                    .map(module -> module.getData(itemStack))
                    .map(data -> data.key)
                    .sorted()
                    .toArray(String[]::new);

            for (SynergyData synergy : synergies) {
                int variantMatches = 0;
                int moduleMatches = 0;
                for (String variantKey : variantKeys) {
                    if (variantMatches == synergy.moduleVariants.length) {
                        break;
                    }

                    if (variantKey.equals(synergy.moduleVariants[variantMatches])) {
                        variantMatches++;
                    }
                }

                for (String moduleKey : moduleKeys) {
                    if (moduleMatches == synergy.modules.length) {
                        break;
                    }

                    if (moduleKey.equals(synergy.modules[moduleMatches])) {
                        moduleMatches++;
                    }
                }

                if (synergy.moduleVariants.length > 0 && variantMatches == synergy.moduleVariants.length
                        || synergy.modules.length > 0 && moduleMatches == synergy.modules.length) {
                    result.add(synergy);
                }
            }
            return result.toArray(new SynergyData[result.size()]);
        }
        return new SynergyData[0];
    }

    protected ItemStack getStackFromMaterialString(String material) {
        switch (material) {
            case "WOOD":
                return new ItemStack(Blocks.PLANKS, 1);
            case "STONE":
                return new ItemStack(Blocks.COBBLESTONE, 1);
            case "IRON":
                return new ItemStack(Items.IRON_INGOT, 1);
            case "DIAMOND":
                return new ItemStack(Items.DIAMOND, 1);
            case "GOLD":
                return new ItemStack(Items.GOLD_INGOT, 1);
            default:
                return new ItemStack(Blocks.PLANKS, 1);
        }
    }
}
