package com.seniortribe.app.auth;

import android.text.Editable;
import android.text.TextWatcher;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import java.util.function.Consumer;

public final class AuthFields {
    private AuthFields() { }
    public static TextInputEditText bind(AppCompatActivity activity, int id,
                                         String initial, Consumer<String> changed) {
        TextInputEditText field = activity.findViewById(id);
        field.setText(initial);
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                changed.accept(s.toString());
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        return field;
    }
}
