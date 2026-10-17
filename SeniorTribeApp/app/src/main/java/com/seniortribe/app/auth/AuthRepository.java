package com.seniortribe.app.auth;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserInfo;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import com.seniortribe.app.models.User;
import com.seniortribe.app.models.AccountOrganization;
import com.seniortribe.app.models.ProfileUpdateResult;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AuthRepository {
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final GoogleCredentialSession googleCredentialSession;

    public AuthRepository(FirebaseAuth auth, FirebaseFirestore firestore) {
        this(auth, firestore, null);
    }

    public AuthRepository(FirebaseAuth auth, FirebaseFirestore firestore,
                          GoogleCredentialSession googleCredentialSession) {
        this.auth = auth;
        this.firestore = firestore;
        this.googleCredentialSession = googleCredentialSession;
    }

    public FirebaseUser currentUser() { return auth.getCurrentUser(); }

    public boolean isCurrentUser(String uid) {
        FirebaseUser user = currentUser();
        return user != null && user.getUid().equals(uid);
    }

    public Task<FirebaseUser> refreshSession() {
        FirebaseUser user = currentUser();
        if (user == null) return Tasks.forException(new IllegalStateException("SESSION"));
        String uid = user.getUid();
        return user.getIdToken(true).onSuccessTask(token -> isCurrentUser(uid)
                ? Tasks.forResult(user) : Tasks.forException(new IllegalStateException("SESSION")));
    }

    public Task<FirebaseUser> signIn(String email, String password) {
        return auth.signInWithEmailAndPassword(InputValidator.normalizeEmail(email), password)
                .onSuccessTask(result -> result.getUser() == null
                        ? Tasks.forException(new IllegalStateException("SESSION"))
                        : Tasks.forResult(result.getUser()));
    }

    public Task<FirebaseUser> signUp(String name, String email, String password) {
        return auth.createUserWithEmailAndPassword(InputValidator.normalizeEmail(email), password)
                .onSuccessTask(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) return Tasks.forException(new IllegalStateException("SESSION"));
                    UserProfileChangeRequest request = new UserProfileChangeRequest.Builder()
                            .setDisplayName(name.trim()).build();
                    // Auth creation already succeeded. A metadata failure must not create a second account.
                    return user.updateProfile(request).continueWith(task -> user);
                });
    }

    public Task<FirebaseUser> signInWithGoogle(String idToken) {
        if (idToken == null || idToken.trim().isEmpty()) {
            return Tasks.forException(new IllegalStateException("GOOGLE_RESPONSE"));
        }
        // Firebase validates the Google token; account data from the picker is not an authorization grant.
        return auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                .onSuccessTask(result -> result.getUser() == null
                        ? Tasks.forException(new IllegalStateException("SESSION"))
                        : Tasks.forResult(result.getUser()));
    }

    public Task<Void> sendPasswordReset(String email) {
        return auth.sendPasswordResetEmail(InputValidator.normalizeEmail(email));
    }

    public Task<User> ensureProfile(String preferredName) {
        FirebaseUser user = currentUser();
        if (user == null) return Tasks.forException(new IllegalStateException("SESSION"));
        String uid = user.getUid();
        String email = user.getEmail();
        if (email == null) return Tasks.forException(new IllegalStateException("PROFILE"));
        String name = InputValidator.validName(preferredName) ? preferredName.trim()
                : InputValidator.validName(user.getDisplayName()) ? user.getDisplayName().trim()
                : "Thành viên SeniorTribe";
        DocumentReference document = firestore.collection("users").document(uid);

        return firestore.runTransaction(transaction -> {
            if (!isCurrentUser(uid)) throw new IllegalStateException("SESSION");
            DocumentSnapshot existing = transaction.get(document);
            if (existing.exists()) {
                User saved = User.fromDocument(existing);
                if (!uid.equals(saved.getUid()) || !InputValidator.validName(saved.getDisplayName())
                        || saved.getEmail().isEmpty()) throw new IllegalStateException("PROFILE");
                // Preserve every existing role/org/contact field. Do not serialize the DTO back to Firestore.
                return saved;
            }

            // Keep the server-timestamp schema already used by stage 1.
            // Organization roles and memberships are assigned through a trusted administration path.
            Map<String, Object> data = new HashMap<>();
            data.put("uid", uid);
            data.put("displayName", name);
            data.put("email", email);
            data.put("area", "");
            data.put("createdAt", FieldValue.serverTimestamp());
            data.put("updatedAt", FieldValue.serverTimestamp());
            transaction.set(document, data);
            User created = new User(uid, email, name, "");
            created.setCreatedAt(0L);
            created.setDistrict("");
            created.setPhone("");
            created.setOrgId("");
            created.setOrgName("");
            return created;
        });
    }

    public Task<Boolean> hasAssignedMembership(String uid) {
        return firestore.collection("memberships").document(uid).collection("organizations")
                .get(Source.SERVER).continueWith(task -> {
                    if (!task.isSuccessful()) {
                        Exception error = task.getException();
                        if (error != null) throw error;
                        throw new IllegalStateException("MEMBERSHIP");
                    }
                    if (!isCurrentUser(uid)) throw new IllegalStateException("SESSION");
                    boolean assigned = false;
                    for (DocumentSnapshot membership : task.getResult().getDocuments()) {
                        if (MembershipPolicy.hasActiveRoles(membership.get("active"), membership.get("roles"))) {
                            assigned = true;
                        }
                    }
                    return assigned;
                });
    }

    public Task<User> readOwnProfile(String uid) {
        if (!isCurrentUser(uid)) return Tasks.forException(new IllegalStateException("SESSION"));
        return firestore.collection("users").document(uid).get(Source.SERVER).continueWith(task -> {
            if (!task.isSuccessful()) throw failure(task.getException());
            if (!isCurrentUser(uid)) throw new IllegalStateException("SESSION");
            DocumentSnapshot document = task.getResult();
            if (!document.exists()) throw new IllegalStateException("PROFILE");
            User profile = User.fromDocument(document);
            if (!uid.equals(profile.getUid()) || !InputValidator.validName(profile.getDisplayName())
                    || profile.getEmail().isEmpty()) throw new IllegalStateException("PROFILE");
            return profile;
        });
    }

    public Task<ProfileUpdateResult> updateOwnProfile(String uid, String name, String area) {
        if (!isCurrentUser(uid)) return Tasks.forException(new IllegalStateException("SESSION"));
        if (ProfileInput.profile(name, area) != ProfileInput.Problem.NONE) {
            return Tasks.forException(new IllegalArgumentException("PROFILE_INPUT"));
        }
        final String cleanName = name.trim();
        final String cleanArea = area.trim();
        DocumentReference reference = firestore.collection("users").document(uid);
        // An online transaction does not leave a profile edit queued for another session.
        return firestore.runTransaction(transaction -> {
            if (!isCurrentUser(uid)) throw new IllegalStateException("SESSION");
            DocumentSnapshot before = transaction.get(reference);
            if (!before.exists()) throw new IllegalStateException("PROFILE");
            User profile = User.fromDocument(before);
            if (!uid.equals(profile.getUid())) throw new IllegalStateException("PROFILE");
            Map<String, Object> patch = new HashMap<>();
            patch.put("displayName", cleanName);
            patch.put("area", cleanArea);
            patch.put("updatedAt", FieldValue.serverTimestamp());
            transaction.update(reference, patch);
            profile.setDisplayName(cleanName);
            profile.setArea(cleanArea);
            return profile;
        }).onSuccessTask(profile -> {
            if (!isCurrentUser(uid)) return Tasks.forException(new IllegalStateException("SESSION"));
            FirebaseUser user = currentUser();
            return user.updateProfile(new UserProfileChangeRequest.Builder().setDisplayName(cleanName).build())
                    .continueWith(task -> {
                        if (!isCurrentUser(uid)) throw new IllegalStateException("SESSION");
                        // Firestore has committed even if the Auth display-name mirror failed.
                        return new ProfileUpdateResult(profile, task.isSuccessful());
                    });
        });
    }

    public boolean hasPasswordProvider() {
        FirebaseUser user = currentUser();
        if (user == null) return false;
        for (UserInfo provider : user.getProviderData()) {
            if (EmailAuthProvider.PROVIDER_ID.equals(provider.getProviderId())) return true;
        }
        return false;
    }

    public Task<Void> changePassword(String uid, String current, String next, String repeated) {
        if (!isCurrentUser(uid)) return Tasks.forException(new IllegalStateException("SESSION"));
        if (!hasPasswordProvider()) return Tasks.forException(new IllegalStateException("PASSWORD_PROVIDER"));
        if (ProfileInput.password(current, next, repeated) != ProfileInput.Problem.NONE) {
            return Tasks.forException(new IllegalArgumentException("PASSWORD_INPUT"));
        }
        FirebaseUser user = currentUser();
        if (user.getEmail() == null) return Tasks.forException(new IllegalStateException("PROFILE"));
        return user.reauthenticate(EmailAuthProvider.getCredential(user.getEmail(), current))
                .onSuccessTask(ignored -> {
                    if (!isCurrentUser(uid)) return Tasks.forException(new IllegalStateException("SESSION"));
                    return user.updatePassword(next);
                });
    }

    public Task<List<AccountOrganization>> readOwnOrganizations(String uid) {
        if (!isCurrentUser(uid)) return Tasks.forException(new IllegalStateException("SESSION"));
        return firestore.collection("memberships").document(uid).collection("organizations")
                .get(Source.SERVER).onSuccessTask(snapshot -> {
                    if (!isCurrentUser(uid)) return Tasks.forException(new IllegalStateException("SESSION"));
                    List<Task<AccountOrganization>> tasks = new ArrayList<>();
                    for (DocumentSnapshot membership : snapshot.getDocuments()) {
                        AccountOrganization item = AccountOrganization.fromMembership(membership.getId(), membership.getData());
                        tasks.add(firestore.collection("organizations").document(item.id).get(Source.SERVER)
                                .continueWith(task -> {
                                    if (!isCurrentUser(uid)) throw new IllegalStateException("SESSION");
                                    if (!task.isSuccessful()) return item.withStatus(AccountOrganization.Details.UNAVAILABLE);
                                    if (!task.getResult().exists()) return item.withStatus(AccountOrganization.Details.MISSING);
                                    try { return item.withDetails(task.getResult().getData()); }
                                    catch (IllegalStateException malformed) { return item.withStatus(AccountOrganization.Details.INVALID); }
                                }));
                    }
                    return Tasks.<AccountOrganization>whenAllSuccess(tasks).continueWith(task -> {
                        if (!task.isSuccessful()) throw failure(task.getException());
                        if (!isCurrentUser(uid)) throw new IllegalStateException("SESSION");
                        List<AccountOrganization> organizations = new ArrayList<>(task.getResult());
                        organizations.sort((a, b) -> a.id.compareTo(b.id));
                        return organizations;
                    });
                });
    }

    private static Exception failure(Exception error) {
        return error == null ? new IllegalStateException("PROFILE") : error;
    }

    public void signOut() {
        auth.signOut();
        if (googleCredentialSession != null) googleCredentialSession.clearState();
    }
}
