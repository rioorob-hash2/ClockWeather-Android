import os, shutil
src = r"C:\Users\rioor\Documents\DeepSeek Harness\ClockWeather\.tmp"
dst = r"C:\Users\rioor\Documents\DeepSeek Harness\ClockWeather\docs\screenshots"
pairs = [
    ("real.png", "01_widgets_home.png"),
    ("st.png", "02_settings_top.png"),
    ("s5.png", "03_settings_notifications.png"),
    ("notif.png", "04_notification.png"),
    ("tr70.png", "05_transparency_70.png"),
    ("w9.png", "06_widget_4x1_4x2.png"),
]
for s, d in pairs:
    p = os.path.join(src, s)
    if os.path.exists(p):
        shutil.copyfile(p, os.path.join(dst, d))
        print("ok", d)
    else:
        print("нет", s)
