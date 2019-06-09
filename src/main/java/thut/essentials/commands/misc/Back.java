package thut.essentials.commands.misc;

import java.util.function.Predicate;

import net.minecraft.block.BlockState;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import thut.essentials.ThutEssentials;
import thut.essentials.commands.CommandManager;
import thut.essentials.events.MoveEvent;
import thut.essentials.util.BaseCommand;
import thut.essentials.util.ConfigManager;
import thut.essentials.util.Coordinate;
import thut.essentials.util.PlayerDataHandler;

public class Back extends BaseCommand
{
    public Back()
    {
        super("back", 0);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void move(MoveEvent event)
    {
        PlayerDataHandler.getCustomDataTag(event.getMobEntity().getCachedUniqueIdString()).putIntArray("prevPos",
                event.getPos());
    }

    @SubscribeEvent
    public void death(LivingDeathEvent event)
    {
        if (event.getMobEntity() instanceof PlayerEntity)
        {
            BlockPos pos = event.getMobEntity().getPosition();
            int[] loc = new int[] { pos.getX(), pos.getY(), pos.getZ(), event.getMobEntity().dimension };
            PlayerDataHandler.getCustomDataTag(event.getMobEntity().getCachedUniqueIdString()).putIntArray("prevPos",
                    loc);
            PlayerDataHandler.saveCustomData(event.getMobEntity().getCachedUniqueIdString());
        }
    }

    @Override
    public void execute(MinecraftServer server, ICommandSource sender, String[] args) throws CommandException
    {
        ServerPlayerEntity player = getPlayerBySender(sender);
        CompoundNBT tag = PlayerDataHandler.getCustomDataTag(player);
        CompoundNBT tptag = tag.getCompound("tp");
        long last = tptag.getLong("backDelay");
        long time = player.getServer().getWorld(0).getGameTime();
        if (last > time)
        {
            player.sendMessage(
                    CommandManager.makeFormattedComponent("Too Soon between Warp attempt", TextFormatting.RED, false));
            return;
        }
        if (PlayerDataHandler.getCustomDataTag(player).hasKey("prevPos"))
        {
            int[] pos = PlayerDataHandler.getCustomDataTag(player).getIntArray("prevPos");
            Coordinate spot = getBackSpot(pos);
            if (spot == null) throw new CommandException("Error with going back, no space found");
            Predicate<Entity> callback = new Predicate<Entity>()
            {
                @Override
                public boolean test(Entity t)
                {
                    if (!(t instanceof PlayerEntity)) return false;
                    PlayerDataHandler.getCustomDataTag(t.getCachedUniqueIdString()).remove("prevPos");
                    tptag.putLong("backDelay", time + ConfigManager.INSTANCE.backReUseDelay);
                    tag.setTag("tp", tptag);
                    PlayerDataHandler.saveCustomData((PlayerEntity) t);
                    return true;
                }
            };
            ITextComponent teleMess = CommandManager.makeFormattedComponent("Warping to Previous Location",
                    TextFormatting.GREEN);
            Spawn.PlayerMover.setMove(player, ThutEssentials.instance.config.backActivateDelay, spot.dim,
                    new BlockPos(spot.x, spot.y, spot.z), teleMess, Spawn.INTERUPTED, callback, false);
        }
        else
        {
            throw new CommandException("No valid /back destination");
        }
    }

    private Coordinate getBackSpot(int[] pos)
    {
        Coordinate spot = new Coordinate(pos[0], pos[1], pos[2], pos[3]);
        ServerWorld world = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(pos[3]);
        if (world == null) return null;
        BlockPos check = new BlockPos(spot.x, spot.y, spot.z);
        if (valid(check, world)) return spot;
        int r = ConfigManager.INSTANCE.backRangeCheck;
        for (int j = 0; j < r; j++)
            for (int i = 0; i < r; i++)
                for (int k = 0; k < r; k++)
                {
                    spot = new Coordinate(pos[0] + i, pos[1] + j, pos[2] + k, pos[3]);
                    check = new BlockPos(spot.x, spot.y, spot.z);
                    if (valid(check, world)) return spot;
                    spot = new Coordinate(pos[0] - i, pos[1] + j, pos[2] - k, pos[3]);
                    check = new BlockPos(spot.x, spot.y, spot.z);
                    if (valid(check, world)) return spot;
                }
        return null;
    }

    private boolean valid(BlockPos pos, World world)
    {
        BlockState state1 = world.getBlockState(pos);
        BlockState state2 = world.getBlockState(pos.up());
        boolean valid1 = state1 == null || !state1.getMaterial().isSolid();
        boolean valid2 = state2 == null || !state2.getMaterial().isSolid();
        return valid1 && valid2;
    }

}
