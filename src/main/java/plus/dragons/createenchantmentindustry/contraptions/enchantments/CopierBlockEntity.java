package plus.dragons.createenchantmentindustry.contraptions.enchantments;

import com.simibubi.create.content.contraptions.fluids.actors.FillingBySpout;
import com.simibubi.create.content.contraptions.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.contraptions.relays.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.tileEntity.SmartTileEntity;
import com.simibubi.create.foundation.tileEntity.TileEntityBehaviour;
import com.simibubi.create.foundation.tileEntity.behaviour.belt.BeltProcessingBehaviour;
import com.simibubi.create.foundation.tileEntity.behaviour.belt.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.foundation.tileEntity.behaviour.fluid.SmartFluidTankBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;

import java.util.ArrayList;
import java.util.List;

import static com.simibubi.create.foundation.tileEntity.behaviour.belt.BeltProcessingBehaviour.ProcessingResult.HOLD;
import static com.simibubi.create.foundation.tileEntity.behaviour.belt.BeltProcessingBehaviour.ProcessingResult.PASS;

public class CopierBlockEntity extends SmartTileEntity implements IHaveGoggleInformation {

    public static final int FILLING_TIME = 100;
    public static final int TANK_CAPACITY = 3000;
    protected BeltProcessingBehaviour beltProcessing;
    public int processingTicks;
    SmartFluidTankBehaviour tank;
    public ItemStack copyTarget;
    public boolean tooExpensive;
    public CopierBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        processingTicks = -1;
        copyTarget = null;
        tooExpensive = false;
    }

    @Override
    public void addBehaviours(List<TileEntityBehaviour> behaviours) {
        behaviours.add(tank = SmartFluidTankBehaviour.single(this, TANK_CAPACITY));
        behaviours.add(beltProcessing = new BeltProcessingBehaviour(this).whenItemEnters(this::onItemReceived)
                .whileItemHeld(this::whenItemHeld));
        // registerAwardables(behaviours, AllAdvancements.SPOUT, AllAdvancements.FOODS);
    }

    public void tick() {
        super.tick();

        if (processingTicks >= 0) {
            processingTicks--;
        }

        if (processingTicks >= 10 && level.isClientSide)
            spawnProcessingParticles(tank.getPrimaryTank()
                    .getRenderedFluid());
    }

    protected void spawnProcessingParticles(FluidStack fluid) {
        // TODO Change After Model is done
        if (isVirtual())
            return;
        /*Vec3 vec = VecHelper.getCenterOf(worldPosition);
        vec = vec.subtract(0, 8 / 16f, 0);
        ParticleOptions particle = FluidFX.getFluidParticle(fluid);
        level.addAlwaysVisibleParticle(particle, vec.x, vec.y, vec.z, 0, -.1f, 0);*/
    }

    protected BeltProcessingBehaviour.ProcessingResult onItemReceived(TransportedItemStack transported,
                                                                      TransportedItemStackHandlerBehaviour handler) {
        if (handler.tileEntity.isVirtual())
            return PASS;
        if (tooExpensive || copyTarget==null)
            return PASS;
        if (!CopyingBook.valid(transported.stack))
            return PASS;
        if (tank.isEmpty() || CopyingBook.isCorrectInt(copyTarget,getCurrentFluidInTank()))
            return HOLD;
        if (CopyingBook.getRequiredAmountForItem(copyTarget) == -1)
            return PASS;
        return HOLD;
    }

    protected BeltProcessingBehaviour.ProcessingResult whenItemHeld(TransportedItemStack transported,
                                                                    TransportedItemStackHandlerBehaviour handler) {
        if (processingTicks != -1 && processingTicks != 10)
            return HOLD;
        if (tooExpensive || copyTarget==null)
            return PASS;
        if (!CopyingBook.valid(transported.stack))
            return PASS;
        if (tank.isEmpty()|| !CopyingBook.isCorrectInt(copyTarget,getCurrentFluidInTank()))
            return HOLD;
        FluidStack fluid = getCurrentFluidInTank();
        int requiredAmountForItem = CopyingBook.getRequiredAmountForItem(copyTarget);
        if (requiredAmountForItem == -1)
            return PASS;
        if (requiredAmountForItem > fluid.getAmount())
            return HOLD;

        if (processingTicks == -1) {
            processingTicks = FILLING_TIME;
            notifyUpdate();
            return HOLD;
        }

        // Process finished
        ItemStack copy = CopyingBook.print(copyTarget, requiredAmountForItem, transported.stack, fluid);
        List<TransportedItemStack> outList = new ArrayList<>();
        TransportedItemStack held = null;
        TransportedItemStack result = transported.copy();
        result.stack = copy;
        if (!transported.stack.isEmpty())
            held = transported.copy();
        outList.add(result);
        handler.handleProcessingOnItem(transported, TransportedItemStackHandlerBehaviour.TransportedResult.convertToAndLeaveHeld(outList, held));
        tank.getPrimaryHandler()
                .setFluid(fluid);
        notifyUpdate();
        return HOLD;
    }

    private FluidStack getCurrentFluidInTank() {
        return tank.getPrimaryHandler()
                .getFluid();
    }

    @Override
    protected void write(CompoundTag compoundTag, boolean clientPacket) {
        super.write(compoundTag, clientPacket);
        compoundTag.putInt("ProcessingTicks", processingTicks);
        compoundTag.putBoolean("tooExpensive",tooExpensive);
        if (copyTarget != null)
            compoundTag.put("copyTarget", copyTarget.serializeNBT());
    }

    @Override
    protected void read(CompoundTag compoundTag, boolean clientPacket) {
        super.read(compoundTag, clientPacket);
        copyTarget = null;
        processingTicks = compoundTag.getInt("ProcessingTicks");
        tooExpensive = compoundTag.getBoolean("tooExpensive");
        if (compoundTag.contains("copyTarget"))
            copyTarget = ItemStack.of(compoundTag.getCompound("copyTarget"));
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (cap == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY && side != Direction.DOWN)
            return tank.getCapability()
                    .cast();
        return super.getCapability(cap, side);
    }

    @Override
    protected AABB createRenderBoundingBox() {
        // TODO: Change after model is done.
        return super.createRenderBoundingBox().expandTowards(0, -2, 0);
    }

}
