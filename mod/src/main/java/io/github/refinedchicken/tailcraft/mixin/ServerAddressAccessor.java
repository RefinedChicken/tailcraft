package io.github.refinedchicken.tailcraft.mixin;

import com.google.common.net.HostAndPort;
import net.minecraft.client.network.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Raw host access: {@link ServerAddress#getAddress()} IDN-encodes and returns "" for Tailcraft addresses. */
@Mixin(ServerAddress.class)
public interface ServerAddressAccessor {
    @Accessor("hostAndPort") HostAndPort tailcraft$getHostAndPort();
}
