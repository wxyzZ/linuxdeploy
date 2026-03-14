package ru.meefik.linuxdeploy.fragment;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.text.InputFilter;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

import ru.meefik.linuxdeploy.PrefStore;
import ru.meefik.linuxdeploy.R;
import ru.meefik.linuxdeploy.activity.MountsActivity;
import ru.meefik.linuxdeploy.activity.PropertiesActivity;

public class PropertiesFragment extends PreferenceFragmentCompat implements
        Preference.OnPreferenceClickListener, OnSharedPreferenceChangeListener {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setSharedPreferencesName(PrefStore.getPropertiesSharedName());

        Intent i = getActivity().getIntent();
        if (i != null) {
            switch (i.getIntExtra("pref", 0)) {
                case 1:
                    setPreferencesFromResource(R.xml.properties_ssh, rootKey);
                    break;
                case 2:
                    setPreferencesFromResource(R.xml.properties_vnc, rootKey);
                    break;
                case 3:
                    setPreferencesFromResource(R.xml.properties_x11, rootKey);
                    break;
                case 4:
                    setPreferencesFromResource(R.xml.properties_fb, rootKey);
                    break;
                case 5:
                    setPreferencesFromResource(R.xml.properties_run_parts, rootKey);
                    break;
                case 6:
                    setPreferencesFromResource(R.xml.properties_sysv, rootKey);
                    break;
                case 7:
                    setPreferencesFromResource(R.xml.properties_pulse, rootKey);
                    break;
                default:
                    setPreferencesFromResource(R.xml.properties, rootKey);
                    setupSuitePreferenceClickListener();
            }
        }

        initSummaries(getPreferenceScreen());
    }

    private void setupSuitePreferenceClickListener() {
        ListPreference suite = findPreference("suite");
        if (suite != null) {
            suite.setOnPreferenceClickListener(preference -> {
                showSuiteSelectionDialog((ListPreference) preference);
                return true; // consume click, prevent default ListPreference dialog
            });
        }
    }

    private static final int SUITE_MAX_LENGTH = 64;
    private static final String SUITE_VALID_PATTERN = "^[a-zA-Z0-9._\\-]+$";

    private void showSuiteSelectionDialog(ListPreference suite) {
        String currentValue = suite.getValue();

        // Always load original predefined entries from resource to avoid stale appended custom values
        CharSequence[] predefinedValues = new CharSequence[0];
        ListPreference distrib = findPreference("distrib");
        if (distrib != null && distrib.getValue() != null) {
            int suiteValuesId = PrefStore.getResourceId(getContext(),
                    distrib.getValue() + "_suite_values", "array");
            if (suiteValuesId > 0) {
                predefinedValues = getContext().getResources().getTextArray(suiteValuesId);
            }
        }
        int predefinedCount = predefinedValues.length;

        // "Custom / Manual input" is always FIRST
        int totalCount = predefinedCount + 1;
        CharSequence[] allEntries = new CharSequence[totalCount];
        CharSequence[] allEntryValues = new CharSequence[totalCount];

        allEntries[0] = getString(R.string.suite_custom_input);
        allEntryValues[0] = "__custom__";
        System.arraycopy(predefinedValues, 0, allEntries, 1, predefinedCount);
        System.arraycopy(predefinedValues, 0, allEntryValues, 1, predefinedCount);

        // Highlight current predefined item; if value is custom/not-found, highlight "Custom" (index 0)
        int checkedItem = 0;
        if (currentValue != null && !currentValue.isEmpty()) {
            for (int i = 1; i < totalCount; i++) {
                if (currentValue.equals(allEntryValues[i].toString())) {
                    checkedItem = i;
                    break;
                }
            }
        }

        final CharSequence[] finalPredefinedValues = predefinedValues;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_title_suite_preference)
                .setSingleChoiceItems(allEntries, checkedItem, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) {
                        showSuiteCustomInputDialog(suite, currentValue);
                    } else {
                        // Restore entries to clean predefined-only state
                        suite.setEntries(finalPredefinedValues);
                        suite.setEntryValues(finalPredefinedValues);
                        suite.setValue(allEntryValues[which].toString());
                        setSummary(suite, false);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showSuiteCustomInputDialog(ListPreference suite, String currentValue) {
        EditText editText = new EditText(requireContext());
        editText.setSingleLine(true);
        editText.setHint(R.string.suite_input_hint);
        editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(SUITE_MAX_LENGTH)});
        if (currentValue != null && currentValue.length() > 0 && !currentValue.equals("__custom__")) {
            editText.setText(currentValue);
            editText.setSelection(currentValue.length());
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_title_suite_custom)
                .setMessage(R.string.dialog_message_suite_custom)
                .setView(editText)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String customValue = editText.getText().toString().trim();
                if (customValue.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.suite_error_empty,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                if (customValue.length() > SUITE_MAX_LENGTH) {
                    Toast.makeText(requireContext(), getString(R.string.suite_error_too_long, SUITE_MAX_LENGTH),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!customValue.matches(SUITE_VALID_PATTERN)) {
                    Toast.makeText(requireContext(), R.string.suite_error_invalid_chars,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                suite.setValue(customValue);
                setSummary(suite, false);
                PrefStore.dumpProperties(requireContext());
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        switch (preference.getKey()) {
            case "ssh_properties": {
                Intent intent = new Intent(getContext(), PropertiesActivity.class);
                intent.putExtra("pref", 1);
                startActivity(intent);
                break;
            }
            case "gui_properties": {
                Intent intent = new Intent(getContext(), PropertiesActivity.class);

                ListPreference graphics = findPreference("graphics");
                if (graphics != null && graphics.getValue() != null) {
                    switch (graphics.getValue()) {
                        case "vnc":
                            intent.putExtra("pref", 2);
                            break;
                        case "x11":
                            intent.putExtra("pref", 3);
                            break;
                        case "fb":
                            intent.putExtra("pref", 4);
                            break;
                    }
                }

                startActivity(intent);
                break;
            }
            case "init_properties": {
                Intent intent = new Intent(getContext(), PropertiesActivity.class);

                ListPreference init = findPreference("init");
                if (init != null && init.getValue() != null) {
                    switch (init.getValue()) {
                        case "run-parts":
                            intent.putExtra("pref", 5);
                            break;
                        case "sysv":
                            intent.putExtra("pref", 6);
                            break;
                    }
                }

                startActivity(intent);
                break;
            }
            case "pulse_properties": {
                Intent intent = new Intent(getContext(), PropertiesActivity.class);
                intent.putExtra("pref", 7);
                startActivity(intent);
                break;
            }
            case "mounts_editor": {
                Intent intent = new Intent(getContext(), MountsActivity.class);
                startActivity(intent);
                break;
            }
        }

        return true;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference pref = findPreference(key);
        if (pref != null) {
            setSummary(pref, true);
        }
    }

    private void initSummaries(PreferenceGroup pg) {
        for (int i = 0; i < pg.getPreferenceCount(); ++i) {
            Preference p = pg.getPreference(i);
            if (p instanceof PreferenceGroup)
                initSummaries((PreferenceGroup) p);
            else
                setSummary(p, false);
            if (p instanceof PreferenceScreen)
                p.setOnPreferenceClickListener(this);
        }
    }

    private void setSummary(Preference pref, boolean init) {
        if (pref == null) return;
        if (pref instanceof EditTextPreference) {
            EditTextPreference editPref = (EditTextPreference) pref;
            pref.setSummary(editPref.getText());

            String text = editPref.getText();
            if (editPref.getKey().equals("dns")
                    && android.text.TextUtils.isEmpty(text)) {
                pref.setSummary(getString(R.string.summary_dns_preference));
            }
            if (editPref.getKey().equals("disk_size")
                    && "0".equals(text)) {
                pref.setSummary(getString(R.string.summary_disk_size_preference));
            }
            if (editPref.getKey().equals("user_password") &&
                    android.text.TextUtils.isEmpty(text)) {
                editPref.setText(PrefStore.generatePassword());
                pref.setSummary(editPref.getText());
            }
            if (editPref.getKey().equals("user_name")) {
                String userName = text != null ? text : "";
                String privilegedUsers = getString(R.string.privileged_users).replaceAll("android", userName);
                EditTextPreference editPrivilegedUsers = findPreference("privileged_users");
                if (editPrivilegedUsers != null) {
                    editPrivilegedUsers.setText(privilegedUsers);
                    editPrivilegedUsers.setSummary(privilegedUsers);
                }
            }
        }

        if (pref instanceof ListPreference) {
            ListPreference listPref = (ListPreference) pref;
            CharSequence entry = listPref.getEntry();
            // When value is custom (e.g. suite manual input), getEntry() is null, use getValue()
            pref.setSummary(entry != null ? entry : listPref.getValue());

            if (listPref.getKey().equals("distrib")) {
                ListPreference suite = findPreference("suite");
                ListPreference architecture = findPreference("arch");
                EditTextPreference sourcepath = findPreference("source_path");

                String distributionStr = listPref.getValue();

                // suite: load predefined entries for current distrib
                String savedSuiteValue = suite.getValue();
                int suiteValuesId = PrefStore.getResourceId(getContext(),
                        distributionStr + "_suite_values", "array");
                if (suiteValuesId > 0) {
                    CharSequence[] predefinedSuiteValues = getContext().getResources().getTextArray(suiteValuesId);
                    // Check if saved value is custom (not in predefined list)
                    boolean isCustomValue = savedSuiteValue != null && savedSuiteValue.length() > 0;
                    if (isCustomValue) {
                        for (CharSequence v : predefinedSuiteValues) {
                            if (savedSuiteValue.equals(v.toString())) {
                                isCustomValue = false;
                                break;
                            }
                        }
                    }
                    if (isCustomValue) {
                        // Append custom value so ListPreference doesn't silently clear it
                        CharSequence[] newValues = new CharSequence[predefinedSuiteValues.length + 1];
                        System.arraycopy(predefinedSuiteValues, 0, newValues, 0, predefinedSuiteValues.length);
                        newValues[predefinedSuiteValues.length] = savedSuiteValue;
                        suite.setEntries(newValues);
                        suite.setEntryValues(newValues);
                    } else {
                        suite.setEntries(predefinedSuiteValues);
                        suite.setEntryValues(predefinedSuiteValues);
                    }
                    if (init) {
                        // On distrib change: reset suite to the default for the new distrib
                        int suiteId = PrefStore.getResourceId(getContext(), distributionStr + "_suite", "string");
                        if (suiteId > 0) {
                            String defaultSuite = getString(suiteId);
                            if (defaultSuite.length() > 0) {
                                suite.setValue(defaultSuite);
                            }
                        }
                    }
                }
                CharSequence suiteEntry = suite.getEntry();
                suite.setSummary(suiteEntry != null ? suiteEntry : suite.getValue());
                suite.setEnabled(true);

                // architecture
                int architectureValuesId = PrefStore.getResourceId(getContext(),
                        distributionStr + "_arch_values", "array");
                if (architectureValuesId > 0) {
                    architecture.setEntries(architectureValuesId);
                    architecture.setEntryValues(architectureValuesId);
                }
                String archValue = architecture.getValue();
                if (init || archValue == null || archValue.isEmpty()) {
                    int architectureId = PrefStore.getResourceId(getContext(),
                            PrefStore.getArch() + "_" + distributionStr
                                    + "_arch", "string");
                    if (architectureId > 0) {
                        String architectureStr = getString(architectureId);
                        if (architectureStr.length() > 0)
                            architecture.setValue(architectureStr);
                    }
                }
                architecture.setSummary(architecture.getEntry());
                architecture.setEnabled(true);

                // source path
                if (init || android.text.TextUtils.isEmpty(sourcepath.getText())) {
                    int sourcepathId = PrefStore
                            .getResourceId(getContext(), PrefStore.getArch() + "_"
                                    + distributionStr + "_source_path", "string");
                    if (sourcepathId > 0) {
                        sourcepath.setText(getString(sourcepathId));
                    }
                }
                sourcepath.setSummary(sourcepath.getText());
                sourcepath.setEnabled(true);

                // RootFS
                if (distributionStr.equals("rootfs")) {
                    // suite
                    suite.setEnabled(false);
                    // architecture
                    architecture.setEnabled(false);
                    // source path
                    if (init) {
                        String archiveFile = getString(R.string.rootfs_archive);
                        sourcepath.setText(archiveFile);
                    }
                    sourcepath.setSummary(sourcepath.getText());
                    sourcepath.setEnabled(true);
                }
            }
            if (listPref.getKey().equals("arch") && init) {
                ListPreference distribution = findPreference("distrib");
                EditTextPreference sourcepath = findPreference("source_path");

                String architectureStr = PrefStore.getArch(listPref.getValue());
                String distributionStr = distribution.getValue();

                int sourcePathId = PrefStore.getResourceId(getContext(), architectureStr
                        + "_" + distributionStr + "_source_path", "string");
                if (sourcePathId > 0) {
                    sourcepath.setText(getString(sourcePathId));
                }

                sourcepath.setSummary(sourcepath.getText());
            }
            if (listPref.getKey().equals("target_type")) {
                EditTextPreference targetpath = findPreference("target_path");
                EditTextPreference disksize = findPreference("disk_size");
                ListPreference fstype = findPreference("fs_type");

                switch (listPref.getValue()) {
                    case "file":
                        if (init) {
                            targetpath.setText(getString(R.string.target_path_file));
                        }
                        disksize.setEnabled(true);
                        fstype.setEnabled(true);
                        break;
                    case "directory":
                        if (init) {
                            targetpath.setText(getString(R.string.target_path_directory));
                        }
                        disksize.setEnabled(false);
                        fstype.setEnabled(false);
                        break;
                    case "partition":
                        if (init) {
                            targetpath.setText(getString(R.string.target_path_partition));
                        }
                        disksize.setEnabled(false);
                        fstype.setEnabled(true);
                        break;
                    case "ram":
                        if (init) {
                            targetpath.setText(getString(R.string.target_path_ram));
                        }
                        disksize.setEnabled(true);
                        fstype.setEnabled(false);
                        break;
                    case "custom":
                        if (init) {
                            targetpath.setText(getString(R.string.target_path_custom));
                        }
                        disksize.setEnabled(false);
                        fstype.setEnabled(false);
                        break;
                }
            }
        }
    }
}
