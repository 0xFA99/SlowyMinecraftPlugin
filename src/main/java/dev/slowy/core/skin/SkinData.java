package dev.slowy.core.skin;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Immutable representation of a player's skin textures and Mojang cryptographic signature.
 */
@NullMarked
public record SkinData(
        String name,
        String value,
        String signature,
        @Nullable String textureUrl,
        long updatedAt
) {

    public static final SkinData STEVE_SKIN = new SkinData(
            "Steve",
            "ewogICJ0aW1lc3RhbXAiIDogMTcxODAyODU1MjgwMywKICAicHJvZmlsZUlkIiA6ICIxYjQwYzcxMGZjMTY0NmQ2OTIxOTVmYzY3YzZlMTE0ZCIsCiAgInByb2ZpbGVOYW1lIiA6ICJ3c3pvbHNvbiIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9mZGJkYmUxN2IyZGFjN2ZjMmQxNThjZGU5MWI3ODhiZDBkOTRiOWMzYWM2M2JmNjM5YzBjNDdmMDUyYzc4MTg0IgogICAgfQogIH0KfQ==",
            "iChDm3B72apIDxJskEpvzG0+4s++q7Aeh67++WDHOJviaY1udCI7SGXOsHZtx0O5GM1sOz4PA1KFi+hINZxBMTfNnaEGrlNYzBajRpyZ65Vhf0SmTtoCIUhg3YEdKaZS5LaY/nmY75aOlCjItsV9FXy/Mh9W08suMsrO0CwcTxRsTO4UzgjfmN6kwS0+0MvgRZS3cfeV3+4K2tz2aEjtuaOSKO9d68AF2h/narstC4pOXk6A5kCNwZ0Ojz6XqU+rVrM7qI5KQ1Iep6FyInoEvJelHcVyrYW/lCDbbBp33aFWNxPWqPFycrO24BhUCJovXXiunLypM3HdvmOA7gG+l6a92/q+CG1a2h5YHtf1wgLy9Maau+jEKXtfLLnS7ARqFzUbc+HvW10r8edcN1iiaYt+Q3KE+fvqmAeBPYNUklXZZMKL4uQOINjRj5Ykgo5L0dvZi4cJyHGUy+Kvg+g46oegfO9SyS1gA0wmiUOo1ZtecJnnFbKnIFtgDoRwyO+QJe4EtiNLA4TeygWnu5+wGygD6PZF0PfrD0aKh0u6O7FmeKzZenuDFa6lXdLXCMtgCMc45wtGp92Kznc784QWAXUXCFcl8a28CIOXxZLnzxcnMxJk+eQJA1rfoMb06Dapv+HsVzfin8J6L1oaAUe4NaUmr4dm7U9NIL5SPPBRap4=",
            "https://textures.minecraft.net/texture/fdbdbe17b2dac7fc2d158cde91b788bd0d94b9c3ac63bf639c0c47f052c78184",
            0L
    );

    public static final SkinData ALEX_SKIN = new SkinData(
            "Alex",
            "eyJ0aW1lc3RhbXAiOjE1MDMwOTM3MzA4MDksInByb2ZpbGVJZCI6IjVlMjA0OTA4ZGMxMTQwNWQ4NGU0MGU0NDQzYmU0OTViIiwicHJvZmlsZU5hbWUiOiJSZWNsYW1lQm9yZCIsInNpZ25hdHVyZVJlcXVpcmVkIjp0cnVlLCJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzUyZGU5YzE3MzFjOTJhYWE3OGZmYTZlMjA2NzkzN2NiODhlYjFlYmVkYzg1NTU2YmVkODVlMzlmYjJhMmEifX19",
            "sO9+rCWqFnFld/lSsRNkesMwDJTPVaRjTCE5Gg6yx86ATp9EPcVe2t6COJvYPOIbEq/wc31MR2yxDaQbXMd2M/onn07wPjo8yHfZSe2sjm51XRV8U3Q4TRJVhmekkHmXdH6xjKWV4Qez6VEw2335ZBBSYY4H1/oVvkJUzSqV6dC2wxgNO6iJg2wkIJGjqb4vrJXPOCTOYJ6K8oblibC/AabWnxfbAEJDbLphf7roQkaPG8x95lOcTwlkJCOMez7bRCOIV8BlcNoGPlFY2dDC3/065+rddw7T6mzpLSaZXBISOX96UsHHmqLpp7C6R9cQrybGK7Q5bQVJmW2+AirXWBbcxQ48IR30wiYhZ23vzdAlDmnKaE3Lo0LJHmi3t4O0TC+HJmHcV1d5Laf59BlQqMaeB8XEQLiRLj04LzD8DvgWt3ISeUyfUeT8u5MSjy/bIFRRzzeUdQSk6a/1WM08mPia1X/JrhW1WlsuEWX2IRRhNx/48U+ZUKwuU6hbTnnd36tJe4p3i0jgBtAxMzMs9Dra6Lh5AqTKOAw29me+lPe03G+E85VxY6dN761bbjWE/8CfRZBWcUcBEEy0/7FWQN2jHKm5Z8jejmJK6EyOxE4fAoP4cIe+PdlM884v6/UV+5WxLPzCLHNWx5mDFL2tjpCKohRkkMQzukKCXobOL9A=",
            "https://textures.minecraft.net/texture/c52de9c1731c92aaa78ffa6e2067937cb88eb1ebedc85556bed85e39fb2a2a",
            0L
    );

    public boolean isValid() {
        return value != null && !value.isBlank() && signature != null && !signature.isBlank();
    }

    public boolean isExpired(int ttlDays) {
        if (ttlDays <= 0 || updatedAt <= 0) return false;
        long maxAgeMs = ttlDays * 86_400_000L;
        return (System.currentTimeMillis() - updatedAt) > maxAgeMs;
    }
}
