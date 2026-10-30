package ru.admincontrol.cw;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Immutable, testable selection geometry used by the CW brush and schematics. */
public record CwSelection(Point first, Point second, int activePoint) {
	public record Point(RegistryKey<World> world, BlockPos pos) {}

	public CwSelection { if (activePoint != 1 && activePoint != 2) activePoint = 1; }
	public static CwSelection empty() { return new CwSelection(null, null, 1); }
	public CwSelection active(int point) { return new CwSelection(first, second, point); }
	public CwSelection set(int point, RegistryKey<World> world, BlockPos pos) {
		Point value = new Point(world, pos.toImmutable());
		return point == 2 ? new CwSelection(first, value, activePoint) : new CwSelection(value, second, activePoint);
	}
	public boolean complete() { return first != null && second != null; }
	public boolean sameWorld() { return complete() && first.world().equals(second.world()); }
	public BlockBox box() { return complete() ? BlockBox.create(first.pos(), second.pos()) : null; }
	public long volume() {
		BlockBox b = box();
		return b == null ? 0L : (long)b.getBlockCountX() * b.getBlockCountY() * b.getBlockCountZ();
	}
}
