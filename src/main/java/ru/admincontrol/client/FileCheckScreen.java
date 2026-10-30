package ru.admincontrol.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import ru.admincontrol.net.ModPackets;
import ru.admincontrol.screen.FileCheckScreenHandler;
import ru.admincontrol.util.PlayerFileEntry;
import ru.admincontrol.util.PlayerFileService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Проверка файлов игрока: вкладки папок, переход по папкам и архивам (путь сверху кликабельный),
 * поиск по имени или по тексту только внутри открытого места, поиск внутри открытого файла, скачивание.
 */
public class FileCheckScreen extends HandledScreen<FileCheckScreenHandler> {
	private static final int W = 390;
	private static final int H = 254;
	private static final int LIST_Y = 80;
	private static final int ROW = 17;
	private static final int ROWS = 9;
	private static final int LINE = 10;
	private static final int LINES = ROWS * ROW / LINE;
	private static final int BTN_W = 52;
	private static final int TEXT_X = 24;
	private static final String[] FOLDERS = {"resourcepacks", "mods", "shaderpacks", "config"};
	private static final String[] LABELS = {"Ресурспаки", "Моды", "Шейдеры", "Конфиги"};
	private static final Set<String> ARCHIVES = Set.of("zip", "jar");
	private static final Set<String> TEXTS = Set.of("txt", "json", "json5", "properties", "toml", "cfg", "conf", "ini",
			"yml", "yaml", "mcmeta", "lang", "log", "md", "vsh", "fsh", "gsh", "csh", "glsl", "xml", "js", "snbt", "options");
	private static final Set<String> IMAGES = Set.of("png", "jpg", "jpeg", "gif", "bmp", "tga");

	private static final int FOLDER_TAB = 0xFFD9A441;
	private static final int FOLDER_BODY = 0xFFF0C25A;
	private static final int FOLDER_SHADE = 0xFFB98A2E;
	private static final int ARCHIVE_COLOR = 0xFF8E6BD8;
	private static final int TEXT_COLOR = 0xFF6FA8DC;
	private static final int IMAGE_COLOR = 0xFF6CC56C;
	private static final int CLASS_COLOR = 0xFFE39B4B;
	private static final int OTHER_COLOR = 0xFF9A9A9A;
	private static final int LINK = 0x9FD3FF;
	private static final int MATCH_BG = 0x90B8860B;

	private enum Mode { LIST, RESULTS, TEXT }

	/** Ответ сервера (разобранный пакет). */
	public static final class Response {
		public int token;
		public int op;
		public String folder;
		public String path;
		public String query;
		public int kind;
		public boolean truncated;
		public final List<PlayerFileEntry> entries = new ArrayList<>();
		public String text = "";

		public static Response read(PacketByteBuf buf) {
			Response r = new Response();
			r.token = buf.readVarInt();
			r.op = buf.readByte();
			r.folder = buf.readString(32);
			r.path = buf.readString(PlayerFileEntry.PATH_MAX);
			r.query = buf.readString(64);
			r.kind = buf.readByte();
			r.truncated = buf.readBoolean();
			if (r.kind == PlayerFileService.KIND_ENTRIES) {
				int n = buf.readVarInt();
				for (int i = 0; i < n; i++) {
					r.entries.add(PlayerFileEntry.read(buf));
				}
			} else if (r.kind == PlayerFileService.KIND_TEXT) {
				r.text = buf.readString(100_000);
			} else {
				r.text = buf.readString(300);
			}
			return r;
		}
	}

	/** Элемент пути сверху: подпись и куда ведёт (null — текущий файл, не кликается). */
	private record Crumb(String label, String target) {
	}

	private final AcScrollBar bar = new AcScrollBar();
	private TextFieldWidget search;
	private String folder = FOLDERS[0];
	/** Открытое место: папка "a/b" или папка в архиве "a/pack.zip!/assets/". */
	private String loc = "";
	private String viewPath = "";
	private Mode mode = Mode.LIST;
	private boolean grepMode;
	private boolean grepResults;
	private boolean textFromResults;
	private String resultsQuery = "";
	private String resultsStatus = "";
	private final List<PlayerFileEntry> list = new ArrayList<>();
	private final List<PlayerFileEntry> results = new ArrayList<>();
	private String listStatus = "";
	private final List<String> lines = new ArrayList<>();
	private final List<Integer> origin = new ArrayList<>();
	private String highlight = "";
	private final List<Integer> matches = new ArrayList<>();
	private int matchIndex = -1;
	private int pendingLine;
	private String pendingQuery = "";
	private final List<Crumb> crumbs = new ArrayList<>();
	private final List<int[]> crumbBoxes = new ArrayList<>();
	private String status = "";
	private boolean statusError;
	private boolean loading;
	private int token;
	private int scroll;
	private boolean requested;

	public FileCheckScreen(FileCheckScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = W;
		this.backgroundHeight = H;
		this.playerInventoryTitleY = 1000;
	}

	@Override
	protected void init() {
		super.init();
		String text = this.search == null ? "" : this.search.getText();
		this.search = new TextFieldWidget(this.textRenderer, this.x + 8, this.y + 41, searchW(), 14, Text.empty());
		this.search.setMaxLength(60);
		this.search.setText(text);
		rebuild();
		if (!this.requested) {
			this.requested = true;
			request(PlayerFileService.OP_LIST, this.folder, "", "");
		}
	}

	private int searchW() {
		return W - 16 - 56 - 86 - 8;
	}

	private String hint() {
		if (this.mode == Mode.TEXT) {
			return "Найти в этом файле…";
		}
		return this.grepMode ? "Текст внутри файлов этой папки…" : "Имя файла или папки здесь…";
	}

	private List<PlayerFileEntry> shown() {
		return this.mode == Mode.RESULTS ? this.results : this.list;
	}

	private void rebuild() {
		this.clearChildren();
		int tw = (W - 16 - 12) / 4;
		for (int i = 0; i < FOLDERS.length; i++) {
			String f = FOLDERS[i];
			boolean sel = f.equals(this.folder);
			this.addDrawableChild(ButtonWidget.builder(Text.literal((sel ? "§e" : "") + LABELS[i]), b -> openTab(f))
					.dimensions(this.x + 8 + i * (tw + 4), this.y + 20, tw, 16).build());
		}
		AcHint.apply(this.search, hint());
		this.addDrawableChild(this.search);
		int fx = this.x + 8 + searchW() + 4;
		this.addDrawableChild(ButtonWidget.builder(Text.literal("Найти"), b -> doSearch())
				.dimensions(fx, this.y + 40, 56, 16).build());
		ButtonWidget kind = ButtonWidget.builder(Text.literal(this.mode == Mode.TEXT ? "В файле"
				: this.grepMode ? "По тексту" : "По имени"), b -> {
			this.grepMode = !this.grepMode;
			rebuild();
		}).dimensions(fx + 60, this.y + 40, 86, 16).build();
		kind.active = this.mode != Mode.TEXT;
		this.addDrawableChild(kind);
		ButtonWidget back = ButtonWidget.builder(Text.literal("‹ Назад"), b -> back())
				.dimensions(this.x + 8, this.y + 60, 50, 16).build();
		back.active = canBack();
		this.addDrawableChild(back);
		if (this.mode == Mode.TEXT) {
			String file = this.viewPath;
			this.addDrawableChild(ButtonWidget.builder(Text.literal("Скачать"), b -> download(file, fileName(file)))
					.dimensions(this.x + W - 8 - BTN_W, this.y + 60, BTN_W, 16).build());
		} else {
			List<PlayerFileEntry> rows = shown();
			for (int i = 0; i < ROWS; i++) {
				int index = this.scroll + i;
				if (index >= rows.size()) {
					break;
				}
				PlayerFileEntry e = rows.get(index);
				if (e.dir) {
					continue;
				}
				this.addDrawableChild(ButtonWidget.builder(Text.literal("Скачать"), b -> download(e.path, e.name()))
						.dimensions(btnX(), this.y + LIST_Y + i * ROW + 1, BTN_W, 15).build());
			}
		}
		buildCrumbs();
		AcNavButtons.add(this);
	}

	private int btnX() {
		return this.x + W - 14 - BTN_W;
	}

	private void request(int op, String f, String p, String q) {
		this.token++;
		this.loading = true;
		this.status = op == PlayerFileService.OP_GREP ? "Ищу «" + q + "» внутри файлов… (до 25 сек)"
				: op == PlayerFileService.OP_SEARCH ? "Ищу «" + q + "»…" : "Загрузка…";
		this.statusError = false;
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(this.token);
		buf.writeByte(op);
		buf.writeString(f, 32);
		buf.writeString(p == null ? "" : p, PlayerFileEntry.PATH_MAX);
		buf.writeString(q == null ? "" : q, 64);
		ClientPlayNetworking.send(ModPackets.FILES_ADMIN, buf);
	}

	public void accept(Response r) {
		if (r.token != this.token) {
			return;
		}
		this.loading = false;
		this.statusError = false;
		if (r.kind == PlayerFileService.KIND_ERROR) {
			this.status = r.text;
			this.statusError = true;
			if (r.op == PlayerFileService.OP_LIST && r.path.isEmpty()) {
				this.folder = PlayerFileService.allowedFolder(r.folder) ? r.folder : this.folder;
				this.loc = "";
				this.mode = Mode.LIST;
				this.list.clear();
				this.listStatus = r.text;
				this.scroll = 0;
			}
			rebuild();
			return;
		}
		this.scroll = 0;
		if (r.kind == PlayerFileService.KIND_TEXT) {
			this.textFromResults = this.mode == Mode.RESULTS || (this.mode == Mode.TEXT && this.textFromResults);
			this.mode = Mode.TEXT;
			this.viewPath = r.path;
			wrapText(r.text);
			this.status = "Строк: " + (this.origin.isEmpty() ? 0 : this.origin.get(this.origin.size() - 1))
					+ (r.truncated ? " · показано начало файла" : "");
			this.highlight = "";
			this.matches.clear();
			this.matchIndex = -1;
			if (!this.pendingQuery.isEmpty()) {
				find(this.pendingQuery, this.pendingLine);
			}
			this.pendingQuery = "";
			this.pendingLine = 0;
			rebuild();
			return;
		}
		if (r.op == PlayerFileService.OP_SEARCH || r.op == PlayerFileService.OP_GREP) {
			this.mode = Mode.RESULTS;
			this.grepResults = r.op == PlayerFileService.OP_GREP;
			this.resultsQuery = r.query;
			this.results.clear();
			this.results.addAll(r.entries);
			this.resultsStatus = (this.grepResults ? "Совпадений в тексте: " : "Найдено: ") + r.entries.size()
					+ (r.truncated ? " (показаны первые)" : "") + " · «" + r.query + "» в " + place();
			this.status = this.resultsStatus;
		} else {
			this.mode = Mode.LIST;
			this.folder = r.folder;
			this.loc = r.op == PlayerFileService.OP_VIEW && !r.path.contains("!/") ? r.path + "!/" : r.path;
			this.list.clear();
			this.list.addAll(r.entries);
			long dirs = r.entries.stream().filter(e -> e.dir).count();
			this.listStatus = r.entries.isEmpty() ? "Пусто" : "Папок: " + dirs + " · Файлов: " + (r.entries.size() - dirs)
					+ (r.truncated ? " (показаны первые)" : "");
			this.status = this.listStatus;
		}
		rebuild();
	}

	private void wrapText(String text) {
		this.lines.clear();
		this.origin.clear();
		int width = W - 16 - 14;
		int number = 0;
		for (String raw : text.replace("\r", "").replace("\t", "    ").split("\n", -1)) {
			number++;
			if (this.lines.size() >= 8000) {
				break;
			}
			if (raw.isEmpty()) {
				this.lines.add("");
				this.origin.add(number);
				continue;
			}
			for (StringVisitable part : this.textRenderer.getTextHandler().wrapLines(raw, width, Style.EMPTY)) {
				this.lines.add(part.getString());
				this.origin.add(number);
			}
		}
	}

	/** Поиск в открытом файле; startLine — строка файла, к которой перейти (0 — следующее совпадение). */
	private void find(String query, int startLine) {
		String q = query.toLowerCase(Locale.ROOT);
		boolean same = q.equals(this.highlight);
		this.highlight = q;
		this.matches.clear();
		for (int i = 0; i < this.lines.size(); i++) {
			if (this.lines.get(i).toLowerCase(Locale.ROOT).contains(q)) {
				this.matches.add(i);
			}
		}
		int target = -1;
		if (startLine > 0) {
			int first = this.origin.indexOf(startLine);
			for (int i = 0; i < this.matches.size(); i++) {
				if (this.matches.get(i) >= Math.max(0, first)) {
					target = i;
					break;
				}
			}
			if (target < 0 && first >= 0) {
				this.matchIndex = -1;
				setScroll(first - 3);
				this.status = "Строка " + startLine + " · «" + query + "»";
				return;
			}
		} else if (same && this.matchIndex >= 0 && !this.matches.isEmpty()) {
			target = (this.matchIndex + 1) % this.matches.size();
		} else {
			for (int i = 0; i < this.matches.size(); i++) {
				if (this.matches.get(i) >= this.scroll) {
					target = i;
					break;
				}
			}
			if (target < 0 && !this.matches.isEmpty()) {
				target = 0;
			}
		}
		if (target < 0) {
			this.matchIndex = -1;
			this.status = "«" + query + "» в этом файле не найдено";
			this.statusError = true;
			return;
		}
		this.matchIndex = target;
		int line = this.matches.get(target);
		setScroll(line - 3);
		this.statusError = false;
		this.status = "Совпадение " + (target + 1) + " из " + this.matches.size() + " · строка " + this.origin.get(line)
				+ " · Enter — следующее";
	}

	private void openTab(String f) {
		this.folder = f;
		this.loc = "";
		request(PlayerFileService.OP_LIST, f, "", "");
	}

	private void go(String target) {
		request(target.contains("!/") ? PlayerFileService.OP_VIEW : PlayerFileService.OP_LIST, this.folder, target, "");
	}

	private void doSearch() {
		String q = this.search.getText().trim();
		if (q.isEmpty()) {
			this.status = "Введите текст для поиска.";
			this.statusError = true;
			return;
		}
		if (this.mode == Mode.TEXT) {
			find(q, 0);
			return;
		}
		request(this.grepMode ? PlayerFileService.OP_GREP : PlayerFileService.OP_SEARCH, this.folder, this.loc, q);
	}

	private void open(PlayerFileEntry e) {
		if (e.dir) {
			go(e.path);
			return;
		}
		this.pendingLine = this.mode == Mode.RESULTS && this.grepResults ? e.line : 0;
		this.pendingQuery = this.mode == Mode.RESULTS && this.grepResults ? this.resultsQuery : "";
		request(PlayerFileService.OP_VIEW, e.folder, e.path, "");
	}

	private void download(String file, String name) {
		request(PlayerFileService.OP_DOWNLOAD, this.folder, file, "");
		this.loading = false;
		this.statusError = false;
		this.status = "Скачивание «" + name + "» — результат придёт в чат.";
	}

	private boolean canBack() {
		return this.mode != Mode.LIST || !this.loc.isEmpty();
	}

	private void back() {
		if (this.mode == Mode.TEXT && this.textFromResults) {
			this.mode = Mode.RESULTS;
			this.textFromResults = false;
			this.scroll = 0;
			this.status = this.resultsStatus;
			this.statusError = false;
			rebuild();
			return;
		}
		if (this.mode != Mode.LIST) {
			showList();
			return;
		}
		String up = parent(this.loc);
		if (up != null) {
			go(up);
		}
	}

	private void showList() {
		this.mode = Mode.LIST;
		this.textFromResults = false;
		this.scroll = 0;
		this.status = this.listStatus;
		this.statusError = false;
		rebuild();
	}

	/** Место на уровень выше; null — уже корень вкладки. */
	private static String parent(String location) {
		int bang = location.indexOf("!/");
		if (bang < 0) {
			return location.isEmpty() ? null : parentDir(location);
		}
		String outer = location.substring(0, bang);
		String inner = location.substring(bang + 2);
		if (inner.endsWith("/")) {
			inner = inner.substring(0, inner.length() - 1);
		}
		if (inner.isEmpty()) {
			return parentDir(outer);
		}
		int slash = inner.lastIndexOf('/');
		return outer + "!/" + (slash < 0 ? "" : inner.substring(0, slash + 1));
	}

	private static String parentDir(String p) {
		int idx = p.lastIndexOf('/');
		return idx < 0 ? "" : p.substring(0, idx);
	}

	private static String fileName(String p) {
		String s = p.endsWith("/") ? p.substring(0, p.length() - 1) : p;
		int idx = Math.max(s.lastIndexOf('/'), s.lastIndexOf("!/") + 1);
		return idx < 0 ? s : s.substring(idx + 1);
	}

	private String tabLabel() {
		for (int i = 0; i < FOLDERS.length; i++) {
			if (FOLDERS[i].equals(this.folder)) {
				return LABELS[i];
			}
		}
		return this.folder;
	}

	private String place() {
		if (this.loc.isEmpty()) {
			return tabLabel();
		}
		String name = fileName(this.loc.endsWith("!/") ? this.loc.substring(0, this.loc.length() - 2) : this.loc);
		return "«" + name + "»";
	}

	private void buildCrumbs() {
		this.crumbs.clear();
		this.crumbs.add(new Crumb(tabLabel(), ""));
		String p = this.mode == Mode.TEXT ? this.viewPath : this.loc;
		int bang = p.indexOf("!/");
		String outer = bang < 0 ? p : p.substring(0, bang);
		String inner = bang < 0 ? "" : p.substring(bang + 2);
		StringBuilder acc = new StringBuilder();
		String[] parts = outer.isEmpty() ? new String[0] : outer.split("/");
		for (int i = 0; i < parts.length; i++) {
			if (acc.length() > 0) {
				acc.append('/');
			}
			acc.append(parts[i]);
			boolean lastOuter = i == parts.length - 1;
			String target = lastOuter && bang >= 0 ? acc + "!/" : acc.toString();
			if (lastOuter && bang < 0 && this.mode == Mode.TEXT) {
				target = null;
			}
			this.crumbs.add(new Crumb(parts[i], target));
		}
		if (bang >= 0 && !inner.isEmpty()) {
			boolean file = !inner.endsWith("/");
			String[] in = (file ? inner : inner.substring(0, inner.length() - 1)).split("/");
			StringBuilder innerAcc = new StringBuilder();
			for (int i = 0; i < in.length; i++) {
				innerAcc.append(in[i]).append('/');
				boolean lastFile = file && i == in.length - 1;
				this.crumbs.add(new Crumb(in[i], lastFile ? null : outer + "!/" + innerAcc));
			}
		}
	}

	private int total() {
		return this.mode == Mode.TEXT ? this.lines.size() : shown().size();
	}

	private int visible() {
		return this.mode == Mode.TEXT ? LINES : ROWS;
	}

	private int maxScroll() {
		return Math.max(0, total() - visible());
	}

	private int rowAt(double mouseX, double mouseY) {
		if (this.mode == Mode.TEXT) {
			return -1;
		}
		int rx = this.x + 6;
		int top = this.y + LIST_Y;
		List<PlayerFileEntry> rows = shown();
		int index = this.scroll + (int) ((mouseY - top) / ROW);
		if (mouseX < rx || mouseY < top || mouseY >= top + ROWS * ROW || index >= rows.size()) {
			return -1;
		}
		int right = rows.get(index).dir ? this.x + W - 14 : btnX() - 2;
		return mouseX < right ? index : -1;
	}

	private int crumbAt(double mouseX, double mouseY) {
		double mx = mouseX - this.x;
		double my = mouseY - this.y;
		for (int[] box : this.crumbBoxes) {
			if (mx >= box[0] && mx < box[0] + box[1] && my >= 62 && my < 74) {
				return box[2];
			}
		}
		return -1;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (maxScroll() > 0 && this.bar.inTrack(mouseX, mouseY, this.x + W - 12, this.y + LIST_Y, ROWS * ROW)) {
			this.bar.dragging = true;
			setScroll(this.bar.click(mouseY, this.y + LIST_Y, ROWS * ROW, maxScroll()));
			return true;
		}
		if (button == 0 && !this.loading) {
			int crumb = crumbAt(mouseX, mouseY);
			if (crumb >= 0 && crumb < this.crumbs.size() && this.crumbs.get(crumb).target() != null) {
				String target = this.crumbs.get(crumb).target();
				if (target.equals(this.loc)) {
					if (this.mode != Mode.LIST) {
						showList();
					}
				} else {
					go(target);
				}
				return true;
			}
			int row = rowAt(mouseX, mouseY);
			if (row >= 0) {
				open(shown().get(row));
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (this.bar.dragging && maxScroll() > 0) {
			setScroll(this.bar.click(mouseY, this.y + LIST_Y, ROWS * ROW, maxScroll()));
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		this.bar.dragging = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		int step = this.mode == Mode.TEXT ? 3 : 1;
		setScroll(this.scroll - (int) Math.signum(amount) * step);
		return true;
	}

	private void setScroll(int value) {
		int next = Math.max(0, Math.min(maxScroll(), value));
		if (next != this.scroll) {
			this.scroll = next;
			if (this.mode != Mode.TEXT) {
				rebuild();
			}
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (this.search != null && this.search.isFocused()) {
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				doSearch();
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				this.search.setFocused(false);
				return true;
			}
			if (this.client != null && this.client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
				return true;
			}
			if (this.search.keyPressed(keyCode, scanCode, modifiers)) {
				return true;
			}
		}
		if (keyCode == GLFW.GLFW_KEY_BACKSPACE && canBack() && !this.loading) {
			back();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (this.search != null && this.search.charTyped(chr, modifiers)) {
			return true;
		}
		return super.charTyped(chr, modifiers);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		AcHint.tick(this.search, hint());
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		AcTheme.panel(context, this.x, this.y, W, H, 18);
		AcTheme.footer(context, this.x, this.y, W, H);
		if (this.mode == Mode.TEXT) {
			return;
		}
		List<PlayerFileEntry> rows = shown();
		for (int i = 0; i < ROWS; i++) {
			int index = this.scroll + i;
			if (index >= rows.size()) {
				break;
			}
			if (rows.get(index).dir) {
				context.fill(this.x + 6, this.y + LIST_Y + i * ROW, this.x + W - 14, this.y + LIST_Y + (i + 1) * ROW - 1,
						0x22F0C25A);
			}
		}
		int hover = rowAt(mouseX, mouseY);
		if (hover >= 0) {
			int i = hover - this.scroll;
			AcTheme.row(context, this.x + 6, this.y + LIST_Y + i * ROW, W - 20, ROW - 1);
		}
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		AcTheme.titleCentered(this.textRenderer, context, "Файлы игрока " + this.handler.targetNick, 8, 5, W - 16);
		drawCrumbs(context, mouseX, mouseY);
		if (this.mode == Mode.TEXT) {
			drawText(context);
		} else {
			drawRows(context);
		}
		if (maxScroll() > 0) {
			this.bar.draw(context, W - 12, LIST_Y, ROWS * ROW, maxScroll(), visible(), this.scroll);
		}
		AcTheme.clipped(this.textRenderer, context, this.status, 8, H - 13, W - 16,
				this.statusError ? AcTheme.RED : AcTheme.MUTED);
	}

	private void drawCrumbs(DrawContext context, int mouseX, int mouseY) {
		this.crumbBoxes.clear();
		int left = 64;
		int right = this.mode == Mode.TEXT ? W - 8 - BTN_W - 6 : W - 8;
		String sep = " / ";
		int sepW = this.textRenderer.getWidth(sep);
		int start = 1;
		int width = this.textRenderer.getWidth(this.crumbs.get(0).label());
		for (int i = 1; i < this.crumbs.size(); i++) {
			width += sepW + this.textRenderer.getWidth(this.crumbs.get(i).label());
		}
		String dots = "…";
		while (width > right - left && start < this.crumbs.size() - 1) {
			width -= sepW + this.textRenderer.getWidth(this.crumbs.get(start).label());
			start++;
		}
		int hover = crumbAt(mouseX, mouseY);
		int cx = left;
		for (int i = 0; i < this.crumbs.size(); i++) {
			if (i > 0 && i < start) {
				continue;
			}
			if (i > 0) {
				String s = i == start && start > 1 ? sep + dots + sep : sep;
				context.drawText(this.textRenderer, s, cx, 64, AcTheme.MUTED, false);
				cx += this.textRenderer.getWidth(s);
			}
			Crumb c = this.crumbs.get(i);
			boolean current = i == this.crumbs.size() - 1;
			String label = AcTheme.ellipsize(this.textRenderer, c.label(), Math.max(20, right - cx));
			int w = this.textRenderer.getWidth(label);
			boolean link = c.target() != null && !(current && this.mode == Mode.LIST);
			int color = current ? AcTheme.GOLD : link ? LINK : AcTheme.TEXT;
			if (link && hover == i) {
				color = AcTheme.TEXT;
				context.fill(cx, 73, cx + w, 74, 0xFFFFFFFF);
			}
			context.drawText(this.textRenderer, label, cx, 64, color, false);
			if (link) {
				this.crumbBoxes.add(new int[] {cx, w, i});
			}
			cx += w;
			if (cx >= right) {
				break;
			}
		}
	}

	private void drawText(DrawContext context) {
		int current = this.matchIndex >= 0 && this.matchIndex < this.matches.size() ? this.matches.get(this.matchIndex) : -1;
		for (int i = 0; i < LINES; i++) {
			int index = this.scroll + i;
			if (index >= this.lines.size()) {
				break;
			}
			String line = this.lines.get(index);
			int ty = LIST_Y + i * LINE;
			if (index == current) {
				context.fill(6, ty - 1, W - 14, ty + LINE - 1, 0x30FFFFFF);
			}
			if (!this.highlight.isEmpty()) {
				String lower = line.toLowerCase(Locale.ROOT);
				int at = lower.length() == line.length() ? lower.indexOf(this.highlight) : -1;
				while (at >= 0) {
					int x0 = 8 + this.textRenderer.getWidth(line.substring(0, at));
					int x1 = x0 + this.textRenderer.getWidth(line.substring(at, at + this.highlight.length()));
					context.fill(x0 - 1, ty - 1, x1 + 1, ty + 9, MATCH_BG);
					at = lower.indexOf(this.highlight, at + this.highlight.length());
				}
			}
			context.drawText(this.textRenderer, line, 8, ty, AcTheme.TEXT, false);
		}
	}

	private void drawRows(DrawContext context) {
		List<PlayerFileEntry> rows = shown();
		int nameRight = btnX() - this.x - 6;
		for (int i = 0; i < ROWS; i++) {
			int index = this.scroll + i;
			if (index >= rows.size()) {
				break;
			}
			PlayerFileEntry e = rows.get(index);
			int top = LIST_Y + i * ROW;
			int ty = top + 5;
			drawIcon(context, e, 9, top + 3);
			if (e.dir) {
				String info = e.size > 0 ? "папка · " + e.size + " эл." : "папка";
				int infoW = this.textRenderer.getWidth(info);
				int arrowX = W - 24;
				context.drawText(this.textRenderer, "›", arrowX, ty, AcTheme.GOLD, false);
				context.drawText(this.textRenderer, info, arrowX - 6 - infoW, ty, AcTheme.MUTED, false);
				AcTheme.clipped(this.textRenderer, context, label(e), TEXT_X, ty, arrowX - 12 - infoW - TEXT_X, AcTheme.TEXT);
				continue;
			}
			if (this.mode == Mode.RESULTS && this.grepResults) {
				String where = label(e) + (e.line > 0 ? ":" + e.line : "");
				int whereW = Math.min(this.textRenderer.getWidth(where), (nameRight - TEXT_X) * 2 / 5);
				AcTheme.clipped(this.textRenderer, context, where, TEXT_X, ty, whereW, AcTheme.TEXT);
				int nx = TEXT_X + whereW + 8;
				drawMatch(context, e.note, nx, ty, nameRight - nx);
				continue;
			}
			String info = PlayerFileService.human(e.size);
			int infoW = this.textRenderer.getWidth(info);
			context.drawText(this.textRenderer, info, nameRight - infoW, ty, AcTheme.MUTED, false);
			AcTheme.clipped(this.textRenderer, context, label(e), TEXT_X, ty, nameRight - infoW - 8 - TEXT_X, AcTheme.TEXT);
		}
		if (rows.isEmpty() && !this.loading) {
			String empty = this.mode == Mode.RESULTS ? "Ничего не найдено в " + place() : "Пусто";
			context.drawText(this.textRenderer, empty, (W - this.textRenderer.getWidth(empty)) / 2,
					LIST_Y + 40, AcTheme.MUTED, false);
		}
	}

	/** Найденный фрагмент: совпадение подсвечено. */
	private void drawMatch(DrawContext context, String note, int x, int y, int maxW) {
		if (maxW <= 10) {
			return;
		}
		String text = AcTheme.ellipsize(this.textRenderer, note, maxW);
		String q = this.resultsQuery.toLowerCase(Locale.ROOT);
		String lower = text.toLowerCase(Locale.ROOT);
		int at = q.isEmpty() || lower.length() != text.length() ? -1 : lower.indexOf(q);
		if (at < 0) {
			context.drawText(this.textRenderer, text, x, y, AcTheme.MUTED, false);
			return;
		}
		String before = text.substring(0, at);
		String hit = text.substring(at, at + q.length());
		int hx = x + this.textRenderer.getWidth(before);
		int hw = this.textRenderer.getWidth(hit);
		context.drawText(this.textRenderer, before, x, y, AcTheme.MUTED, false);
		context.fill(hx - 1, y - 1, hx + hw + 1, y + 9, MATCH_BG);
		context.drawText(this.textRenderer, hit, hx, y, AcTheme.GOLD, false);
		context.drawText(this.textRenderer, text.substring(at + q.length()), hx + hw, y, AcTheme.MUTED, false);
	}

	private void drawIcon(DrawContext context, PlayerFileEntry e, int x, int y) {
		if (e.dir) {
			context.fill(x, y, x + 5, y + 2, FOLDER_TAB);
			context.fill(x, y + 2, x + 12, y + 10, FOLDER_BODY);
			context.fill(x, y + 9, x + 12, y + 10, FOLDER_SHADE);
			return;
		}
		String ext = ext(e.path);
		if (ARCHIVES.contains(ext)) {
			context.fill(x, y, x + 12, y + 10, ARCHIVE_COLOR);
			context.fill(x, y, x + 12, y + 2, 0xFFB49CEB);
			for (int i = 2; i < 10; i += 2) {
				context.fill(x + 5, y + i, x + 7, y + i + 1, 0xFFE6DDFA);
			}
			return;
		}
		int band = TEXTS.contains(ext) ? TEXT_COLOR : IMAGES.contains(ext) ? IMAGE_COLOR
				: "class".equals(ext) ? CLASS_COLOR : OTHER_COLOR;
		context.fill(x + 1, y - 1, x + 11, y + 11, 0xFFE6E6E6);
		context.fill(x + 8, y - 1, x + 11, y + 2, 0xFF9A9A9A);
		context.fill(x + 1, y + 6, x + 11, y + 11, band);
	}

	private static String ext(String path) {
		String name = fileName(path);
		int dot = name.lastIndexOf('.');
		return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	/** Имя в списке; в результатах поиска — путь от открытого места, "!/" архива показан как « › ». */
	private String label(PlayerFileEntry e) {
		if (this.mode != Mode.RESULTS) {
			return e.name();
		}
		String p = e.path;
		if (this.loc.contains("!/")) {
			if (p.startsWith(this.loc)) {
				p = p.substring(this.loc.length());
			}
		} else if (!this.loc.isEmpty() && p.startsWith(this.loc + "/")) {
			p = p.substring(this.loc.length() + 1);
		}
		if (p.endsWith("/")) {
			p = p.substring(0, p.length() - 1);
		}
		return p.replace("!/", " › ");
	}
}
