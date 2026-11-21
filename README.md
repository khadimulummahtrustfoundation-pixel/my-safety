# 🛡️ NafsGuard Lite

লক করলে ৩০ দিন আনইন্সটল ও Private DNS সেটিংস বন্ধ রাখার অ্যাপ।

## GitHub দিয়ে APK বানানো
1. GitHub-এ নতুন repo বানান, এই সব ফাইল push করুন।
2. **Actions** ট্যাব → **Build APK** চলবে (শেষ হতে ~৩-৫ মিনিট)।
3. শেষ হলে রান খুলে **Artifacts → MySafety-APK** ডাউনলোড করুন, ফোনে ইন্সটল করুন।

## ফোনে সেটআপ
1. অ্যাপ খুলে **Device Admin** পারমিশন দিন।
2. **Accessibility** চালু করুন। (Android 13+ এ গ্রে থাকলে: Settings → Apps → NafsGuard Lite → ⋮ → *Allow restricted settings*)
3. (প্রস্তাবিত) **Device Owner** সেট করুন — সবচেয়ে শক্তিশালী মোড:
   - ফোনে কোনো Google/অন্য অ্যাকাউন্ট লগইন থাকা চলবে না (দরকার হলে সাময়িক সরান)
   - Developer options → USB debugging চালু করে কম্পিউটারে সংযুক্ত করুন
   - `adb shell dpm set-device-owner com.mysafety.app/.MyDeviceAdminReceiver`
4. **৩০ দিনের জন্য লক করুন** চাপুন।

## Device Owner থাকলে অতিরিক্ত যা হয়
- আনইন্সটল সম্পূর্ণ ব্লক, Force stop/Clear data বন্ধ
- Safe mode, Factory reset, VPN, ঘড়ি বদল, Developer options/ADB বন্ধ
- Private DNS জোর করে `family-filter-dns.cleanbrowsing.org` সেট (পরিবর্তনযোগ্য নয়)
- Accessibility নিজে নিজে চালু থাকে

৩০ দিন শেষে অ্যাপে **মুক্ত করুন (Release)** চাপলে সব ছেড়ে দেয়।

## মেয়াদ শেষের অ্যালার্ম
লক শুরুর সময়ই `AlarmManager.setAlarmClock` দিয়ে অফলাইন অ্যালার্ম সেট হয়। ইন্টারনেট ছাড়াও বাজে, রিবুট করলে আবার সেট হয়। Android 13+ এ নোটিফিকেশন পারমিশন দিন।
