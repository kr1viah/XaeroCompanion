package net.kr1v.xaerocompanion.config;

import com.ibm.icu.impl.Pair;
import kr1v.malilibApi.annotation.Config;
import kr1v.malilibApi.config.custom.ConfigCycle;
import kr1v.malilibApi.config.custom.EnumBackedCycleConfig;
import kr1v.malilibApi.config.plus.ConfigDoublePlus;
import kr1v.malilibApi.config.plus.ConfigHotkeyPlus;
import kr1v.malilibApi.config.plus.ConfigStringPlus;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;

@Config("xaerocompanion")
public class Main {
	public static final ConfigStringPlus PING_NAME = new ConfigStringPlus("Ping name", "Ping", "What name the ping waypoints should have");
	public static final ConfigStringPlus PING_INITIALS = new ConfigStringPlus("Ping initials", "!", "What initials the ping waypoints should have");
	public static final ConfigCycle<WaypointColor> PING_COLOR = new EnumBackedCycleConfig.Builder<>("Ping color", WaypointColor.class).initialValue(WaypointColor.GREEN).build();
	public static final ConfigDoublePlus MAX_DISTANCE = new ConfigDoublePlus("Max distance", 3, 0, 15, "Some metric of how close the crosshair should be to the ping to remove it. Set to 0 to disable");

	public static final ConfigHotkeyPlus PING = new ConfigHotkeyPlus("Ping", "MOUSE_MIDDLE", "When this hotkey is pressed, it will place a ping waypoint where you are looking").setAllowExtraKeys(true);

	static {
		PING.setPressCallback(((_, _) -> {
			if (Minecraft.getInstance().level == null || Minecraft.getInstance().player == null) {
				return false;
			}
			if (Minecraft.getInstance().hitResult == null || Minecraft.getInstance().hitResult.getType() == HitResult.Type.MISS) {
				var hit = Minecraft.getInstance().player.pick(2500, 0, false);
				if (hit.getType() == HitResult.Type.MISS) return false;
				Vec3i pos = new Vec3i((int) hit.getLocation().x, (int)hit.getLocation().y, (int)hit.getLocation().z);

				var currentSet = BuiltInHudModules.MINIMAP.getCurrentSession().getWorldManager().getCurrentWorld().getCurrentWaypointSet();

				// check if exists in range
				Pair<Double, Waypoint> toRemoveDistanceWaypoint = null;
				for (Waypoint waypoint : currentSet.getWaypoints()) {
					if (PING_NAME.getStringValue().equals(waypoint.getName()) && PING_INITIALS.getStringValue().equals(waypoint.getInitials())) {
						Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();

						Vec3 waypointRenderPos = new Vec3(waypoint.getX() + 0.5, waypoint.getY() + 1, waypoint.getZ() + 0.5);

						Vec3 offset = waypointRenderPos.subtract(camera.position());

						if (offset.dot(new Vec3(camera.forwardVector())) <= 0) continue;

						Vec3 screenPos = Minecraft.getInstance().gameRenderer.projectPointToScreen(waypointRenderPos);

						double dx = screenPos.x * Minecraft.getInstance().getWindow().getWidth() / 2.0;
						double dy = screenPos.y * Minecraft.getInstance().getWindow().getHeight() / 2.0;
						double distanceSqr = dx*dx + dy*dy;

						double radius = MAX_DISTANCE.getDoubleValue() * 10;

						if (distanceSqr <= radius * radius) {
							if (toRemoveDistanceWaypoint == null) toRemoveDistanceWaypoint = Pair.of(distanceSqr, waypoint);
							if (distanceSqr < toRemoveDistanceWaypoint.first) toRemoveDistanceWaypoint = Pair.of(distanceSqr, waypoint);
						}
					}
				}


				if (toRemoveDistanceWaypoint != null) {
					currentSet.remove(toRemoveDistanceWaypoint.second);
					return true;
				}

				Waypoint waypoint = new Waypoint(pos.getX(), pos.getY(), pos.getZ(), PING_NAME.getStringValue(), PING_INITIALS.getStringValue(), PING_COLOR.get(), WaypointPurpose.DESTINATION, true, true);
				currentSet.add(waypoint);
				return true;
			}
			return false;
		}));
	}
}
