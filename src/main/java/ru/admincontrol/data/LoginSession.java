package ru.admincontrol.data;

/** Одна сессия на сервере: вход и (если был) выход. */
public class LoginSession {
	public long joinAt;
	public long leaveAt;

	public LoginSession() {
	}

	public LoginSession(long joinAt) {
		this.joinAt = joinAt;
	}

	public boolean open() {
		return this.joinAt > 0L && this.leaveAt <= 0L;
	}
}
