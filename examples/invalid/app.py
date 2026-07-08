from flask import Flask, render_template

app = Flask("bad")

return 5

@app.route("/")
def home():
    break
    return render_template("missing.html", title=title)

@app.route("/")
def other():
    ghost()
    return "ok"
