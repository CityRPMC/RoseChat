package dev.rosewood.rosechat.hook;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;

public final class DigiDocumentsHook {

    public record CivilDocument(String name, String surname, String fullName, int age,
                                String rawGender, String formattedPronouns) {
    }

    private static volatile boolean initialized;
    private static boolean available;
    private static Method isAvailableMethod;
    private static Method getInstanceMethod;
    private static Method getDocumentMethod;
    private static Method fetchDocumentMethod;
    private static Method nameMethod;
    private static Method surnameMethod;
    private static Method fullNameMethod;
    private static Method ageMethod;
    private static Method genderMethod;

    private DigiDocumentsHook() {

    }

    private static boolean ensureInitialized() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Documents"))
            return false;

        if (!initialized) {
            synchronized (DigiDocumentsHook.class) {
                if (!initialized) {
                    try {
                        Class<?> apiClass = Class.forName("org.cityrp.digidocuments.api.DigiDocumentsAPI");
                        Class<?> docClass = Class.forName("org.cityrp.digidocuments.data.Document");
                        isAvailableMethod = apiClass.getMethod("isAvailable");
                        getInstanceMethod = apiClass.getMethod("get");
                        getDocumentMethod = apiClass.getMethod("getDocument", UUID.class);
                        fetchDocumentMethod = apiClass.getMethod("fetchDocument", UUID.class);
                        nameMethod = docClass.getMethod("name");
                        surnameMethod = docClass.getMethod("surname");
                        fullNameMethod = docClass.getMethod("fullName");
                        ageMethod = docClass.getMethod("age");
                        genderMethod = docClass.getMethod("gender");
                        available = true;
                    } catch (ReflectiveOperationException ignored) {
                        available = false;
                    }
                    initialized = true;
                }
            }
        }

        if (!available)
            return false;

        try {
            return Boolean.TRUE.equals(isAvailableMethod.invoke(null));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static Optional<CivilDocument> getCachedDocument(UUID uuid) {
        if (uuid == null || !ensureInitialized())
            return Optional.empty();

        try {
            Object api = getInstanceMethod.invoke(null);
            Object opt = getDocumentMethod.invoke(api, uuid);
            return convertOptionalDocument(opt);
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    public static CompletableFuture<Optional<CivilDocument>> fetchDocument(UUID uuid) {
        if (uuid == null || !ensureInitialized())
            return CompletableFuture.completedFuture(Optional.empty());

        try {
            Object api = getInstanceMethod.invoke(null);
            CompletableFuture<Optional<?>> future = (CompletableFuture<Optional<?>>) fetchDocumentMethod.invoke(api, uuid);
            return future.thenApply(DigiDocumentsHook::convertOptionalDocument)
                    .exceptionally(ex -> Optional.empty());
        } catch (ReflectiveOperationException ignored) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    private static Optional<CivilDocument> convertOptionalDocument(Object optionalObj) {
        if (!(optionalObj instanceof Optional<?> opt) || opt.isEmpty())
            return Optional.empty();

        try {
            Object doc = opt.get();
            String name = (String) nameMethod.invoke(doc);
            String surname = (String) surnameMethod.invoke(doc);
            String fullName = (String) fullNameMethod.invoke(doc);
            int age = (int) ageMethod.invoke(doc);
            String rawGender = (String) genderMethod.invoke(doc);
            return Optional.of(new CivilDocument(name, surname, fullName, age, rawGender, formatPronouns(rawGender)));
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }

    public static String formatPronouns(String rawGender) {
        if (rawGender == null || rawGender.isBlank())
            return "Not set";

        return switch (rawGender.trim().toLowerCase(Locale.ROOT)) {
            case "he/him", "he_him" -> "He/Him";
            case "she/her", "she_her" -> "She/Her";
            case "they/them", "they_them" -> "They/Them";
            case "other" -> "Other";
            default -> rawGender;
        };
    }

}
