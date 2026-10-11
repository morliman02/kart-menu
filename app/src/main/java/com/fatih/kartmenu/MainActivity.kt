package com.fatih.kartmenu

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.util.UUID

class MainActivity : Activity() {

    private lateinit var store: CardStore
    private lateinit var adapter: CardAdapter
    private lateinit var addressStore: AddressStore
    private lateinit var addressAdapter: AddressAdapter
    private lateinit var signupStore: SignupStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Kart/adres bilgileri ekran görüntüsünde ve son uygulamalar önizlemesinde görünmesin.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_main)

        // Hangi sürümün kurulu olduğu ekranda açıkça görünsün (güncelleme oturdu mu kontrolü).
        runCatching {
            val info = packageManager.getPackageInfo(packageName, 0)
            findViewById<TextView>(R.id.versionLabel).text = "Sürüm ${info.versionName}"
        }

        store = CardStore(this)
        adapter = CardAdapter()
        addressStore = AddressStore(this)
        addressAdapter = AddressAdapter()
        signupStore = SignupStore(this)

        val list = findViewById<ListView>(R.id.cardList)
        list.adapter = adapter
        list.emptyView = findViewById(R.id.emptyView)
        list.setOnItemLongClickListener { _, _, position, _ ->
            confirmDelete(adapter.getItem(position), position)
            true
        }

        val addrList = findViewById<ListView>(R.id.addressList)
        addrList.adapter = addressAdapter
        addrList.emptyView = findViewById(R.id.emptyAddressView)
        addrList.setOnItemLongClickListener { _, _, position, _ ->
            confirmDeleteAddress(addressAdapter.getItem(position), position)
            true
        }

        findViewById<Button>(R.id.addButton).setOnClickListener { showAddDialog() }
        findViewById<Button>(R.id.addAddressButton).setOnClickListener { showAddAddressDialog() }
        findViewById<Button>(R.id.signupButton).setOnClickListener { showSignupDialog() }
        findViewById<Button>(R.id.overlayButton).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        findViewById<Button>(R.id.accessibilityButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Listeden \"${getString(R.string.accessibility_label)}\" servisini açın", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        refreshCards()
        refreshAddresses()
        refreshSignupStatus()
    }

    private fun refreshSignupStatus() {
        val d = signupStore.getData()
        findViewById<TextView>(R.id.signupStatus).text =
            "E-posta: ${d.emails.size} adet · Ad soyad: ${d.names.size} adet · Parola: " +
                if (d.password.isNotBlank()) "var" else "yok"
    }

    private fun refreshPermissions() {
        val overlayOn = Settings.canDrawOverlays(this)
        val accessibilityOn = isAccessibilityServiceEnabled()
        findViewById<TextView>(R.id.overlayStatus).text =
            getString(R.string.overlay_permission) + ": " +
                getString(if (overlayOn) R.string.granted else R.string.not_granted)
        findViewById<TextView>(R.id.accessibilityStatus).text =
            getString(R.string.accessibility_permission) + ": " +
                getString(if (accessibilityOn) R.string.granted else R.string.not_granted)
        findViewById<Button>(R.id.overlayButton).visibility = if (overlayOn) View.GONE else View.VISIBLE
        findViewById<Button>(R.id.accessibilityButton).visibility = if (accessibilityOn) View.GONE else View.VISIBLE
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val mine = ComponentName(this, KartAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == mine }
    }

    private fun refreshCards() {
        adapter.cards = store.getAll()
        adapter.notifyDataSetChanged()
    }

    private fun showAddDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_card, null)
        val numberInput = view.findViewById<EditText>(R.id.numberInput)
        val expiryInput = view.findViewById<EditText>(R.id.expiryInput)
        val holderInput = view.findViewById<EditText>(R.id.holderInput)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.add_card)
            .setView(view)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val number = CardValidation.normalizeNumber(numberInput.text.toString())
                val expiry = CardValidation.normalizeExpiry(expiryInput.text.toString())
                val holder = holderInput.text.toString().trim()

                var ok = true
                if (!CardValidation.isValidNumber(number)) {
                    numberInput.error = "Geçerli bir kart numarası girin"
                    ok = false
                }
                if (expiry == null) {
                    expiryInput.error = "AA/YY biçiminde, geçmemiş bir tarih girin"
                    ok = false
                }
                if (holder.isEmpty()) {
                    holderInput.error = "İsim boş olamaz"
                    ok = false
                }
                if (ok && expiry != null) {
                    store.add(Card(UUID.randomUUID().toString(), number, expiry, holder))
                    refreshCards()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun confirmDelete(card: Card, position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Kart ${position + 1} silinsin mi?")
            .setMessage("${card.holder}\n${card.masked}")
            .setPositiveButton(R.string.delete) { _, _ ->
                store.delete(card.id)
                refreshCards()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------------------------------------------------------- adresler

    private fun refreshAddresses() {
        addressAdapter.addresses = addressStore.getAll()
        addressAdapter.notifyDataSetChanged()
    }

    private fun showAddAddressDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_address, null)
        val fullName = view.findViewById<EditText>(R.id.fullNameInput)
        val phone = view.findViewById<EditText>(R.id.phoneInput)
        val line1 = view.findViewById<EditText>(R.id.line1Input)
        val line2 = view.findViewById<EditText>(R.id.line2Input)
        val line3 = view.findViewById<EditText>(R.id.line3Input)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.add_address)
            .setView(view)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val addressLine1 = line1.text.toString().trim()
                if (addressLine1.isEmpty()) {
                    line1.error = "Adres satırı 1 boş olamaz"
                    return@setOnClickListener
                }
                // Birden fazla telefon: her satır (veya virgül) bir numara, en fazla 5 tanesi.
                val phones = splitLines(phone.text.toString()).take(5)
                addressStore.add(
                    Address(
                        id = UUID.randomUUID().toString(),
                        fullName = fullName.text.toString().trim(),
                        phones = phones,
                        line1 = addressLine1,
                        line2 = line2.text.toString().trim(),
                        line3 = line3.text.toString().trim(),
                    )
                )
                refreshAddresses()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    /** Çok satırlı metni satır veya virgülle ayırıp boşları atar, baştaki/sondaki boşlukları kırpar. */
    private fun splitLines(text: String): List<String> =
        text.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }

    private fun showSignupDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_signup, null)
        val emails = view.findViewById<EditText>(R.id.emailsInput)
        val names = view.findViewById<EditText>(R.id.namesInput)
        val password = view.findViewById<EditText>(R.id.passwordInput)

        // Mevcut bilgileri forma doldur (her biri alt alta).
        val current = signupStore.getData()
        emails.setText(current.emails.joinToString("\n"))
        names.setText(current.names.joinToString("\n"))
        password.setText(current.password)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.signup_title)
            .setView(view)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                signupStore.save(
                    SignupData(
                        emails = splitLines(emails.text.toString()),
                        names = splitLines(names.text.toString()),
                        password = password.text.toString().trim(),
                    )
                )
                refreshSignupStatus()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun confirmDeleteAddress(address: Address, position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Adres ${position + 1} silinsin mi?")
            .setMessage(address.summary)
            .setPositiveButton(R.string.delete) { _, _ ->
                addressStore.delete(address.id)
                refreshAddresses()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private inner class AddressAdapter : BaseAdapter() {
        var addresses: List<Address> = emptyList()

        override fun getCount() = addresses.size
        override fun getItem(position: Int) = addresses[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView ?: layoutInflater.inflate(android.R.layout.simple_list_item_2, parent, false)
            val address = addresses[position]
            row.findViewById<TextView>(android.R.id.text1).text = "Adres ${position + 1}  ·  ${address.title}"
            row.findViewById<TextView>(android.R.id.text2).text = address.summary
            return row
        }
    }

    private inner class CardAdapter : BaseAdapter() {
        var cards: List<Card> = emptyList()

        override fun getCount() = cards.size
        override fun getItem(position: Int) = cards[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView ?: layoutInflater.inflate(android.R.layout.simple_list_item_2, parent, false)
            val card = cards[position]
            row.findViewById<TextView>(android.R.id.text1).text = "Kart ${position + 1}  ·  ${card.masked}"
            row.findViewById<TextView>(android.R.id.text2).text = "${card.holder}  ·  SKT ${card.expiry}"
            return row
        }
    }
}
