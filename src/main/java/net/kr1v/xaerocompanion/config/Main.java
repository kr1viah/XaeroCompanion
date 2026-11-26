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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.set.WaypointSet;

import java.util.ArrayList;
import java.util.List;

@Config("xaerocompanion")
public class Main {
	public static final ConfigStringPlus PING_NAME = new ConfigStringPlus("Ping name", "Ping", "What name the ping waypoints should have");
	public static final ConfigStringPlus PING_INITIALS = new ConfigStringPlus("Ping initials", "!", "What initials the ping waypoints should have");
	public static final ConfigCycle<WaypointColor> PING_COLOR = new EnumBackedCycleConfig.Builder<>("Ping color", WaypointColor.class).initialValue(WaypointColor.GREEN).build();
	public static final ConfigDoublePlus MAX_DISTANCE = new ConfigDoublePlus("Max distance", 3, 0, 15, "Some metric of how close the crosshair should be to the ping to remove it. Set to 0 to disable");

	public static final ConfigHotkeyPlus PING = new ConfigHotkeyPlus("Ping", "MOUSE_MIDDLE", "When this hotkey is pressed, it will place a ping waypoint where you are looking").setAllowExtraKeys(true);
	public static final ConfigHotkeyPlus DELETE_PINGS = new ConfigHotkeyPlus("Delete all pings", "", "When this hotkey is pressed, it will delete all pings").setAllowExtraKeys(true);
	public static final ConfigHotkeyPlus UNDO_LAST_PING = new ConfigHotkeyPlus("Undo ping", "", "When this hotkey is pressed, it will delete the last ping you placed");

	public static boolean isPing(Waypoint waypoint) {
		return PING_NAME.getStringValue().equals(waypoint.getName()) && PING_INITIALS.getStringValue().equals(waypoint.getInitials());
	}

	static {
		DELETE_PINGS.setPressCallback((_ ,_ ) -> {
			WaypointSet currentSet = BuiltInHudModules.MINIMAP.getCurrentSession().getWorldManager().getCurrentWorld().getCurrentWaypointSet();

			List<Waypoint> toRemove = new ArrayList<>();
			for (Waypoint waypoint : currentSet.getWaypoints()) {
				if (isPing(waypoint)) {
					toRemove.add(waypoint);
				}
			}

			if (toRemove.isEmpty()) return false;

			currentSet.removeAll(toRemove);
			return true;
		});

		UNDO_LAST_PING.setPressCallback((_ ,_) -> {
			WaypointSet currentSet = BuiltInHudModules.MINIMAP.getCurrentSession().getWorldManager().getCurrentWorld().getCurrentWaypointSet();

			Waypoint lastPing = null;

			for (Waypoint waypoint : currentSet.getWaypoints()) {
				if (isPing(waypoint)) {
					lastPing = waypoint;
				}
			}

			if (lastPing == null) return false;

			currentSet.remove(lastPing);
			return true;
		});

		PING.setPressCallback(((_, _) -> {
			Minecraft mc = Minecraft.getInstance();
			Camera camera = mc.gameRenderer.mainCamera();
			Entity cameraEntity = camera.entity();
			if (mc.level == null || mc.player == null || cameraEntity == null) {
				return false;
			}

			if (mc.hitResult == null || mc.hitResult.getType() == HitResult.Type.MISS) {
				WaypointSet currentSet = BuiltInHudModules.MINIMAP.getCurrentSession().getWorldManager().getCurrentWorld().getCurrentWaypointSet();

				// check if exists in range
				Pair<Double, Waypoint> toRemoveDistanceWaypoint = null;
				for (Waypoint waypoint : currentSet.getWaypoints()) {
					if (isPing(waypoint)) {
						Vec3 waypointRenderPos = new Vec3(waypoint.getX() + 0.5, waypoint.getY() + 1, waypoint.getZ() + 0.5);

						Vec3 offset = waypointRenderPos.subtract(camera.position());

						if (offset.dot(new Vec3(camera.forwardVector())) <= 0) continue;

						Vec3 screenPos = mc.gameRenderer.projectPointToScreen(waypointRenderPos);

						double dx = screenPos.x * mc.getWindow().getWidth() / 2.0;
						double dy = screenPos.y * mc.getWindow().getHeight() / 2.0;
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

				Vec3 from = cameraEntity.getEyePosition();
				Vec3 viewVector = cameraEntity.getViewVector(0);
				Vec3 to = from.add(viewVector.x * 2500, viewVector.y * 2500, viewVector.z * 2500);
				var hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, cameraEntity));
				if (hit.getType() == HitResult.Type.MISS) return false;
				Vec3i pos = new Vec3i((int) hit.getLocation().x, (int)hit.getLocation().y, (int)hit.getLocation().z);

				Waypoint waypoint = new Waypoint(pos.getX(), pos.getY(), pos.getZ(), PING_NAME.getStringValue(), PING_INITIALS.getStringValue(), PING_COLOR.get(), WaypointPurpose.DESTINATION, true, true);
				currentSet.add(waypoint);
				return true;
			}
			return false;
		}));
	}
}
