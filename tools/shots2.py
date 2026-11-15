import shutil, os
src = r"C:\Users\rioor\Documents\DeepSeek Harness\ClockWeather\.tmp"
dst = r"C:\Users\rioor\Documents\DeepSeek Harness\ClockWeather\docs\screenshots"
for s, d in [("h6.png", "07_widget_4x3.png"), ("wv6.png", "08_weather_screen.png"), ("h5.png", "09_widget_4x3_prev.png")]:
    shutil.copyfile(os.path.join(src, s), os.path.join(dst, d))
    print("ok", d)
