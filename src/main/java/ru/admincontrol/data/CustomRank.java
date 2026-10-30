package ru.admincontrol.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Административный уровень, созданный в /aroles. Хранится в настройках мода вместе с остальными рангами.
 * {@link #level} — внутренний уровень между штатными (10, 15, 20…), поэтому все проверки «уровень не ниже X»
 * относятся к нему так же, как к ближайшему штатному уровню снизу.
 */
public class CustomRank {
	/** Латинский ключ для команд: /set_admin &lt;ник&gt; &lt;ключ&gt;. */
	public String key = "";
	public int level;
	/** Уровень доступа, как его ввели: «2», «2.5». */
	public String access = "";
	/** Название уровня: «Помощник модератора». */
	public String title = "";
	/** Название роли — то, что стоит в [] в чате и в списках. */
	public String role = "";
	/** Формат сообщения в чате: {роль}, {ник}, {сообщение}; поддерживаются &-коды цветов. */
	public String format = "";
	public int color;
	/** Разрешённые команды из списка /aranks. */
	public List<String> commands = new ArrayList<>();
	/** Может ли наказывать администрацию; null — по общему порогу из /aranks. */
	public Boolean punishStaff;

	public CustomRank() {
	}
}
