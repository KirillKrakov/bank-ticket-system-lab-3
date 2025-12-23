package com.example.applicationservice.auth;

public final class AuthHeaderHolder {
    private static final ThreadLocal<String> holder = new ThreadLocal<>();

    private AuthHeaderHolder() {}

    public static void set(String authHeader) {
        holder.set(authHeader);
    }

    public static String get() {
        return holder.get();
    }

    public static void clear() {
        holder.remove();
    }
}

