package com.fontainerepublic.common.landrights;

import com.fontainerepublic.common.network.NetworkBootstrap;
import com.fontainerepublic.common.network.NetworkMessageHandler;
import com.fontainerepublic.server.land.api.MyUsageRightProjection;
import com.fontainerepublic.server.land.model.ParcelId;
import com.fontainerepublic.server.landrights.MyLandRightsRuntime;
import com.fontainerepublic.server.landrights.api.MyLandRightsResponse;
import com.fontainerepublic.server.landrights.api.MyLandRightsService;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Receiving-side handler of the C2S my-usage-rights ledger entry (FR-LAND-002-A
 * §7, message ledger ID 27).
 *
 * <p>Lives in the common package and never holds state: the active
 * {@link MyLandRightsService} is resolved per invocation through the static
 * {@link MyLandRightsRuntime} locator (bound by the module at runtime start).
 * The sender is taken from the connection context; the closed page is pushed
 * back to that player as the matching S2C packet (ID 28) over the shared send
 * service. A missing runtime (shutdown window) is a silent best-effort no-op.</p>
 */
public final class LandRightsMessageHandlers {

    private static final Logger LOGGER = LogUtils.getLogger();

    private LandRightsMessageHandlers() {
    }

    public static NetworkMessageHandler<MyLandRightsRequestPacket> request() {
        return (message, context) -> service(context).ifPresent(service -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                LOGGER.warn("[LandRights] Rejected request without a live C2S sender");
                return;
            }
            Optional<ParcelId> cursor = message.afterParcelId().map(ParcelId::of);
            MyLandRightsResponse response = service.request(
                    sender.getUUID(),
                    message.requestId(),
                    cursor,
                    message.expectedStoreRevision(),
                    message.limit()
            );
            NetworkBootstrap.instance().sendService().trySendToPlayer(
                    sender,
                    toPacket(response)
            );
        });
    }

    private static MyLandRightsPagePacket toPacket(MyLandRightsResponse response) {
        List<MyLandRightsPagePacket.Entry> entries =
                new ArrayList<>(response.entries().size());
        for (MyUsageRightProjection projection : response.entries()) {
            entries.add(new MyLandRightsPagePacket.Entry(
                    projection.parcelId().value(),
                    projection.dimension(),
                    projection.minX(), projection.minY(), projection.minZ(),
                    projection.maxX(), projection.maxY(), projection.maxZ(),
                    projection.zoneType().name(),
                    projection.usageType().name(),
                    projection.grantedAt(),
                    projection.expiresAt(),
                    projection.rightRevision(),
                    projection.parcelRevision()
            ));
        }
        return new MyLandRightsPagePacket(
                response.status().name(),
                response.requestId(),
                response.storeRevision(),
                response.generatedAt(),
                entries,
                response.nextAfterParcelId().map(ParcelId::value),
                response.hasMore()
        );
    }

    private static Optional<MyLandRightsService> service(
            net.minecraftforge.network.NetworkEvent.Context context
    ) {
        Optional<MyLandRightsService> resolved = MyLandRightsRuntime.resolve();
        if (resolved.isEmpty()) {
            LOGGER.warn(
                    "[LandRights] Rejected C2S message for player {}: runtime unavailable",
                    context.getSender() == null ? "?" : context.getSender().getUUID()
            );
        }
        return resolved;
    }
}
