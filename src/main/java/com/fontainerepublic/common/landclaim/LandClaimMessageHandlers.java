package com.fontainerepublic.common.landclaim;

import com.fontainerepublic.common.network.NetworkBootstrap;
import com.fontainerepublic.common.network.NetworkMessageHandler;
import com.fontainerepublic.server.landclaim.LandClaimRuntime;
import com.fontainerepublic.server.landclaim.api.ClaimReceipt;
import com.fontainerepublic.server.landclaim.api.InspectResult;
import com.fontainerepublic.server.landclaim.api.LandClaimService;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/**
 * Receiving-side handlers of the C2S land-claim ledger (FR-LAND-CLAIM-001-A
 * §3/§4, message ledger IDs 23, 25).
 *
 * <p>The handlers live in the common package and never hold state: the active
 * {@link LandClaimService} is resolved per invocation through the static
 * {@link LandClaimRuntime} locator (bound by the module at runtime start). The
 * sender UUID is taken from the connection context; the result is pushed back
 * to that player as the matching S2C packet over the shared send service. A
 * missing runtime (shutdown window) is a silent best-effort no-op.</p>
 */
public final class LandClaimMessageHandlers {

    private static final Logger LOGGER = LogUtils.getLogger();

    private LandClaimMessageHandlers() {
    }

    public static NetworkMessageHandler<LandInspectPacket> inspect() {
        return (message, context) -> service(context).ifPresent(service -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                LOGGER.warn("[LandClaim] Rejected inspect without a live C2S sender");
                return;
            }
            InspectResult result = service.inspect(
                    sender.getUUID(),
                    message.dimension(),
                    message.x(),
                    message.y(),
                    message.z()
            );
            NetworkBootstrap.instance().sendService().trySendToPlayer(
                    sender,
                    toInspectResultPacket(message, result)
            );
        });
    }

    public static NetworkMessageHandler<LandClaimPacket> claim() {
        return (message, context) -> service(context).ifPresent(service -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                LOGGER.warn("[LandClaim] Rejected claim without a live C2S sender");
                return;
            }
            ClaimReceipt receipt = service.claim(
                    sender.getUUID(),
                    message.dimension(),
                    message.x(),
                    message.y(),
                    message.z()
            );
            NetworkBootstrap.instance().sendService().trySendToPlayer(
                    sender,
                    toClaimResultPacket(message, receipt)
            );
        });
    }

    private static LandInspectResultPacket toInspectResultPacket(
            LandInspectPacket request,
            InspectResult result
    ) {
        return new LandInspectResultPacket(
                request.dimension(),
                request.x(),
                request.y(),
                request.z(),
                result.claimable(),
                result.code(),
                result.parcelId(),
                result.minX(), result.minY(), result.minZ(),
                result.maxX(), result.maxY(), result.maxZ(),
                result.zoneType(),
                result.access(),
                result.atMillis()
        );
    }

    private static LandClaimResultPacket toClaimResultPacket(
            LandClaimPacket request,
            ClaimReceipt receipt
    ) {
        return new LandClaimResultPacket(
                request.dimension(),
                request.x(),
                request.y(),
                request.z(),
                receipt.success(),
                receipt.code(),
                receipt.parcelId(),
                receipt.minX(), receipt.minY(), receipt.minZ(),
                receipt.maxX(), receipt.maxY(), receipt.maxZ(),
                receipt.zoneType(),
                receipt.access(),
                receipt.atMillis()
        );
    }

    private static java.util.Optional<LandClaimService> service(
            net.minecraftforge.network.NetworkEvent.Context context
    ) {
        java.util.Optional<LandClaimService> resolved = LandClaimRuntime.resolve();
        if (resolved.isEmpty()) {
            LOGGER.warn(
                    "[LandClaim] Rejected C2S message for player {}: land-claim runtime unavailable",
                    context.getSender() == null ? "?" : context.getSender().getUUID()
            );
        }
        return resolved;
    }
}
