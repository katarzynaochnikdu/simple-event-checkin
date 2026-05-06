import { useState, useEffect } from "react";
import {
  Modal,
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  ActivityIndicator,
  Alert,
  Platform,
  KeyboardAvoidingView,
} from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { fetchCompanyByNip } from "../lib/api";
import type { TicketClass } from "../types";

interface Props {
  visible: boolean;
  eventId: string;
  ticketClasses: TicketClass[];
  onClose: () => void;
  onSubmit: (data: WalkinFormData, checkInNow: boolean) => Promise<void>;
}

export interface WalkinFormData {
  first_name: string;
  last_name: string;
  email: string;
  phone: string;
  company: string;
  nip: string;
  ticket_class_id: string;
  ticket_name: string;
  notes: string;
}

const EMPTY_FORM: WalkinFormData = {
  first_name: "",
  last_name: "",
  email: "",
  phone: "",
  company: "",
  nip: "",
  ticket_class_id: "",
  ticket_name: "",
  notes: "",
};

export default function WalkinFormModal({
  visible,
  eventId,
  ticketClasses,
  onClose,
  onSubmit,
}: Props) {
  const [form, setForm] = useState<WalkinFormData>(EMPTY_FORM);
  const [loading, setLoading] = useState(false);
  const [showTicketPicker, setShowTicketPicker] = useState(false);
  const [gusLoading, setGusLoading] = useState(false);
  const [gusError, setGusError] = useState<string | null>(null);
  const [gusFilled, setGusFilled] = useState(false);

  useEffect(() => {
    if (visible) {
      setForm(EMPTY_FORM);
      setGusError(null);
      setGusFilled(false);
    }
  }, [visible]);

  function set(field: keyof WalkinFormData, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }));
    if (field === "nip") {
      setGusError(null);
      setGusFilled(false);
    }
  }

  function selectTicketClass(tc: TicketClass) {
    setForm((prev) => ({
      ...prev,
      ticket_class_id: tc.ticket_class_id,
      ticket_name: tc.ticket_name,
    }));
    setShowTicketPicker(false);
  }

  async function handleGusLookup() {
    const rawNip = form.nip.replace(/[\s-]/g, "");
    if (rawNip.length !== 10) {
      setGusError("NIP musi mieć 10 cyfr");
      return;
    }
    setGusLoading(true);
    setGusError(null);
    setGusFilled(false);
    try {
      const res = await fetchCompanyByNip(rawNip);
      if (res.success && res.data) {
        setForm((prev) => ({
          ...prev,
          company: res.data!.name || prev.company,
        }));
        setGusFilled(true);
      } else {
        setGusError(res.error || "Nie znaleziono firmy w GUS");
      }
    } catch (e: any) {
      setGusError(e?.message || "Błąd połączenia z GUS");
    } finally {
      setGusLoading(false);
    }
  }

  async function handleSubmit(checkInNow: boolean) {
    if (!form.first_name.trim() || !form.last_name.trim()) {
      Alert.alert("Błąd", "Imię i nazwisko są wymagane.");
      return;
    }
    setLoading(true);
    try {
      await onSubmit(form, checkInNow);
      onClose();
    } catch (e: any) {
      Alert.alert("Błąd", e?.message || "Nie udało się zarejestrować uczestnika");
    } finally {
      setLoading(false);
    }
  }

  const nipDigits = form.nip.replace(/[\s-]/g, "").length;

  return (
    <Modal
      visible={visible}
      transparent
      animationType="slide"
      onRequestClose={onClose}
    >
      <KeyboardAvoidingView
        style={styles.root}
        behavior={Platform.OS === "ios" ? "padding" : "height"}
      >
        <TouchableOpacity style={styles.overlay} activeOpacity={1} onPress={onClose} />

        <View style={styles.sheet}>
          <View style={styles.handle} />

          {/* Header */}
          <View style={styles.header}>
            <Ionicons name="person-add-outline" size={20} color="#0d9488" />
            <Text style={styles.headerTitle}>Rejestracja Walk-in</Text>
            <TouchableOpacity onPress={onClose} style={styles.closeBtn}>
              <Ionicons name="close" size={22} color="#64748b" />
            </TouchableOpacity>
          </View>

          <ScrollView
            style={styles.scroll}
            keyboardShouldPersistTaps="handled"
            showsVerticalScrollIndicator={false}
          >
            {/* Dane podstawowe */}
            <Text style={styles.sectionLabel}>Dane podstawowe *</Text>

            <FieldRow label="Imię *">
              <TextInput
                style={styles.input}
                value={form.first_name}
                onChangeText={(v) => set("first_name", v)}
                placeholder="Imię"
                placeholderTextColor="#94a3b8"
                autoCapitalize="words"
              />
            </FieldRow>

            <FieldRow label="Nazwisko *">
              <TextInput
                style={styles.input}
                value={form.last_name}
                onChangeText={(v) => set("last_name", v)}
                placeholder="Nazwisko"
                placeholderTextColor="#94a3b8"
                autoCapitalize="words"
              />
            </FieldRow>

            {/* Dane kontaktowe */}
            <Text style={styles.sectionLabel}>Dane kontaktowe</Text>

            <FieldRow label="E-mail">
              <TextInput
                style={styles.input}
                value={form.email}
                onChangeText={(v) => set("email", v)}
                placeholder="adres@email.com"
                placeholderTextColor="#94a3b8"
                keyboardType="email-address"
                autoCapitalize="none"
              />
            </FieldRow>

            <FieldRow label="Telefon">
              <TextInput
                style={styles.input}
                value={form.phone}
                onChangeText={(v) => set("phone", v)}
                placeholder="+48 000 000 000"
                placeholderTextColor="#94a3b8"
                keyboardType="phone-pad"
              />
            </FieldRow>

            {/* Firma + NIP */}
            <Text style={styles.sectionLabel}>Firma</Text>

            {/* NIP lookup */}
            <View style={fieldStyles.row}>
              <Text style={fieldStyles.label}>NIP (opcjonalnie)</Text>
              <View style={styles.nipRow}>
                <TextInput
                  style={[styles.input, styles.nipInput]}
                  value={form.nip}
                  onChangeText={(v) => set("nip", v)}
                  placeholder="0000000000"
                  placeholderTextColor="#94a3b8"
                  keyboardType="number-pad"
                  maxLength={13}
                />
                <TouchableOpacity
                  style={[
                    styles.gusBtn,
                    nipDigits !== 10 && styles.gusBtnDisabled,
                    gusFilled && styles.gusBtnSuccess,
                  ]}
                  onPress={handleGusLookup}
                  disabled={gusLoading || nipDigits !== 10}
                  activeOpacity={0.8}
                >
                  {gusLoading ? (
                    <ActivityIndicator size="small" color="#fff" />
                  ) : gusFilled ? (
                    <Ionicons name="checkmark" size={16} color="#fff" />
                  ) : (
                    <Ionicons name="search" size={16} color="#fff" />
                  )}
                  <Text style={styles.gusBtnText}>
                    {gusFilled ? "Pobrano" : "GUS"}
                  </Text>
                </TouchableOpacity>
              </View>
              {gusError ? (
                <View style={styles.gusStatusRow}>
                  <Ionicons name="alert-circle-outline" size={14} color="#dc2626" />
                  <Text style={styles.gusErrorText}>{gusError}</Text>
                </View>
              ) : gusFilled ? (
                <View style={styles.gusStatusRow}>
                  <Ionicons name="checkmark-circle-outline" size={14} color="#059669" />
                  <Text style={styles.gusSuccessText}>Dane pobrane z rejestru GUS</Text>
                </View>
              ) : null}
            </View>

            <FieldRow label="Nazwa firmy">
              <TextInput
                style={[styles.input, gusFilled && styles.inputFilled]}
                value={form.company}
                onChangeText={(v) => set("company", v)}
                placeholder="Nazwa firmy"
                placeholderTextColor="#94a3b8"
              />
            </FieldRow>

            {/* Klasa biletu */}
            {ticketClasses.length > 0 && (
              <>
                <Text style={styles.sectionLabel}>Bilet</Text>
                <FieldRow label="Klasa biletu">
                  <TouchableOpacity
                    style={[styles.input, styles.pickerBtn]}
                    onPress={() => setShowTicketPicker(true)}
                  >
                    <Text
                      style={[
                        styles.pickerText,
                        !form.ticket_name && styles.placeholderText,
                      ]}
                    >
                      {form.ticket_name || "Wybierz klasę biletu..."}
                    </Text>
                    <Ionicons name="chevron-down" size={16} color="#94a3b8" />
                  </TouchableOpacity>
                </FieldRow>
              </>
            )}

            {/* Notatki */}
            <Text style={styles.sectionLabel}>Notatki</Text>
            <TextInput
              style={[styles.input, styles.textarea]}
              value={form.notes}
              onChangeText={(v) => set("notes", v)}
              placeholder="Dodatkowe informacje..."
              placeholderTextColor="#94a3b8"
              multiline
              numberOfLines={3}
              textAlignVertical="top"
            />

            {/* Akcje */}
            <View style={styles.actions}>
              {loading ? (
                <ActivityIndicator color="#0d9488" size="large" style={{ marginVertical: 12 }} />
              ) : (
                <>
                  <TouchableOpacity
                    style={[styles.actionBtn, styles.registerBtn]}
                    onPress={() => handleSubmit(false)}
                  >
                    <Ionicons name="person-add-outline" size={18} color="#fff" />
                    <Text style={styles.actionBtnText}>Zarejestruj</Text>
                  </TouchableOpacity>

                  <TouchableOpacity
                    style={[styles.actionBtn, styles.checkinBtn]}
                    onPress={() => handleSubmit(true)}
                  >
                    <Ionicons name="checkmark-circle-outline" size={18} color="#fff" />
                    <Text style={styles.actionBtnText}>Zarejestruj i odznacz</Text>
                  </TouchableOpacity>
                </>
              )}
            </View>
          </ScrollView>
        </View>
      </KeyboardAvoidingView>

      {/* Ticket class picker */}
      <Modal
        visible={showTicketPicker}
        transparent
        animationType="fade"
        onRequestClose={() => setShowTicketPicker(false)}
      >
        <TouchableOpacity
          style={styles.pickerOverlay}
          activeOpacity={1}
          onPress={() => setShowTicketPicker(false)}
        />
        <View style={styles.pickerSheet}>
          <Text style={styles.pickerTitle}>Wybierz klasę biletu</Text>
          {ticketClasses.map((tc) => (
            <TouchableOpacity
              key={tc.ticket_class_id}
              style={[
                styles.pickerOption,
                form.ticket_class_id === tc.ticket_class_id && styles.pickerOptionActive,
              ]}
              onPress={() => selectTicketClass(tc)}
            >
              <Text
                style={[
                  styles.pickerOptionText,
                  form.ticket_class_id === tc.ticket_class_id && styles.pickerOptionTextActive,
                ]}
              >
                {tc.ticket_name}
              </Text>
              {form.ticket_class_id === tc.ticket_class_id && (
                <Ionicons name="checkmark" size={18} color="#0d9488" />
              )}
            </TouchableOpacity>
          ))}
          <TouchableOpacity
            style={styles.pickerCancel}
            onPress={() => setShowTicketPicker(false)}
          >
            <Text style={styles.pickerCancelText}>Anuluj</Text>
          </TouchableOpacity>
        </View>
      </Modal>
    </Modal>
  );
}

function FieldRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <View style={fieldStyles.row}>
      <Text style={fieldStyles.label}>{label}</Text>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, justifyContent: "flex-end" },
  overlay: { flex: 1, backgroundColor: "rgba(0,0,0,0.4)" },
  sheet: {
    backgroundColor: "#fff",
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    maxHeight: "90%",
  },
  handle: {
    width: 40,
    height: 4,
    backgroundColor: "#e2e8f0",
    borderRadius: 2,
    alignSelf: "center",
    marginTop: 12,
    marginBottom: 4,
  },
  header: {
    flexDirection: "row",
    alignItems: "center",
    padding: 16,
    gap: 10,
    borderBottomWidth: 1,
    borderBottomColor: "#f1f5f9",
  },
  headerTitle: { flex: 1, fontSize: 17, fontWeight: "700", color: "#1e293b" },
  closeBtn: { padding: 4 },
  scroll: { padding: 16 },
  sectionLabel: {
    fontSize: 11,
    fontWeight: "600",
    color: "#94a3b8",
    textTransform: "uppercase",
    letterSpacing: 0.5,
    marginTop: 12,
    marginBottom: 4,
  },
  input: {
    backgroundColor: "#f8fafc",
    borderWidth: 1,
    borderColor: "#e2e8f0",
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 15,
    color: "#1e293b",
    flex: 1,
  },
  inputFilled: {
    borderColor: "#6ee7b7",
    backgroundColor: "#f0fdf4",
  },
  textarea: { minHeight: 72, flex: undefined as any },
  nipRow: { flexDirection: "row", gap: 8, alignItems: "center" },
  nipInput: { flex: 1 },
  gusBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
    backgroundColor: "#0d9488",
    paddingVertical: 10,
    paddingHorizontal: 14,
    borderRadius: 10,
  },
  gusBtnDisabled: { backgroundColor: "#cbd5e1" },
  gusBtnSuccess: { backgroundColor: "#059669" },
  gusBtnText: { color: "#fff", fontWeight: "700", fontSize: 13 },
  gusStatusRow: { flexDirection: "row", alignItems: "center", gap: 4, marginTop: 4 },
  gusErrorText: { fontSize: 12, color: "#dc2626", flex: 1 },
  gusSuccessText: { fontSize: 12, color: "#059669" },
  pickerBtn: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  pickerText: { fontSize: 15, color: "#1e293b" },
  placeholderText: { color: "#94a3b8" },
  actions: { gap: 10, marginTop: 20, marginBottom: 8, paddingBottom: 16 },
  actionBtn: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    borderRadius: 12,
    paddingVertical: 14,
    gap: 8,
  },
  registerBtn: { backgroundColor: "#475569" },
  checkinBtn: { backgroundColor: "#059669" },
  actionBtnText: { fontSize: 16, fontWeight: "600", color: "#fff" },
  pickerOverlay: {
    flex: 1,
    backgroundColor: "rgba(0,0,0,0.4)",
  },
  pickerSheet: {
    backgroundColor: "#fff",
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    padding: 16,
    paddingBottom: 32,
  },
  pickerTitle: {
    fontSize: 16,
    fontWeight: "700",
    color: "#1e293b",
    marginBottom: 12,
    textAlign: "center",
  },
  pickerOption: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingVertical: 14,
    paddingHorizontal: 8,
    borderBottomWidth: 1,
    borderBottomColor: "#f1f5f9",
  },
  pickerOptionActive: { backgroundColor: "#f0fdfa" },
  pickerOptionText: { fontSize: 15, color: "#1e293b" },
  pickerOptionTextActive: { color: "#0d9488", fontWeight: "600" },
  pickerCancel: { marginTop: 12, alignItems: "center", paddingVertical: 12 },
  pickerCancelText: { fontSize: 15, color: "#64748b" },
});

const fieldStyles = StyleSheet.create({
  row: { marginBottom: 8 },
  label: { fontSize: 12, color: "#64748b", marginBottom: 4, fontWeight: "500" },
});
