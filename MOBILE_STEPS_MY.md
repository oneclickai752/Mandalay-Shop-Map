# Mandalay Smart Map APK ကို Laptop မလိုဘဲ Build လုပ်နည်း

**ဒီ ZIP ဖိုင်က APK မဟုတ်သေးပါ။ Android Native App ရဲ့ Source Project ဖြစ်ပါတယ်။**

အလွယ်ဆုံးနည်းက GitHub Actions ကိုသုံးပြီး Cloud မှာ APK Build လုပ်တာပါ။

1. Android ဖုန်းရဲ့ Browser မှာ https://github.com/new သွားပါ။
2. Repository Name ကို `MandalaySmartMap` ထားပြီး Private ရွေးပါ။ Create repository နှိပ်ပါ။
3. ဒီ ZIP ကို Android ဖုန်း Download ထဲမှာထားပါ။ ChatGPT ကို Repository နာမည်ပြောပြီး Source Files ထည့်ခိုင်းနိုင်ပါတယ် (GitHub ကိုချိတ်ထားရမယ်)။ သို့မဟုတ် README ထဲမှာပါတဲ့ Termux နည်းဖြင့် Git Push လုပ်ပါ။
4. GitHub Repository ထဲက Actions > Android APK ကို ဖွင့်ပြီး Build Successful ဖြစ်မဖြစ် စစ်ပါ။
5. `MandalaySmartMap-debug-apk` Artifact ကို Download ဆွဲပြီး ZIP ဖြေပါ။ `app-debug.apk` ကို Install လုပ်ပါ။
6. App ကို Internet နဲ့ဖွင့်ပြီး **Sync Map** နှိပ်ပါ။ Sync ပြီးရင် Offline နဲ့ မြေပုံ၊ ဆိုင်စာရင်းတွေကို စမ်းကြည့်ပါ။

**ပထမ version ၏ ကန့်သတ်ချက်များ**
- ဒီ ZIP ထဲမှာ Mandalay Offline Map Data အကုန် Preload မပါသေးပါ။ Internet ရှိမှ First Sync လုပ်နိုင်ပါမယ်။
- Downtown Mandalay Pilot Map Data ကိုပဲ အရင် Sync လုပ်ပါမယ်။
- OpenStreetMap မှာမရှိတဲ့ ဆိုင်တွေ မပေါ်နိုင်ပါ။
- Facebook/TikTok Auto Search + Google Places API + AI Verification မပါသေးပါ။ အဲဒီဟာတွေက နောက်အဆင့်အတွက် ဖြစ်ပါတယ်။
- Source Project Build ကို ဒီပတ်ဝန်းကျင်မှာ Android SDK မရှိသေးတဲ့အတွက် အတည်ပြုမစမ်းနိုင်သေးပါ။ GitHub Actions Build Log နဲ့ အတူ ပြင်ဆင်နိုင်ပါတယ်။
- ချိတ်ထားတဲ့ GitHub ကို အသုံးပြုရင် Secret Token/Password ကို ChatGPT ထံ မပို့ပါနှင့်။
