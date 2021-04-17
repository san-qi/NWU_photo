# -*- coding: utf-8 -*-
import os
from dehaze import dehaze
from flask import Flask, request, url_for, send_from_directory, redirect
from werkzeug.utils import secure_filename

ALLOWED_EXTENSIONS = set(['png', 'jpg', 'jpeg', 'gif'])

app = Flask(__name__)

imgPath = os.path.join(os.getcwd(), "img")
if not os.path.exists(imgPath):
    os.mkdir(imgPath)
app.config['UPLOAD_FOLDER'] = imgPath

# app.config['MAX_CONTENT_LENGTH'] = 64 * 1024 * 1024


html = '''
    <!DOCTYPE html>
    <title>Upload File</title>
    <h1>图片上传</h1>
    <form method=post enctype=multipart/form-data>
        <input type=file name=file>
        <input type=submit value=上传>
    </form>'''


def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1] in ALLOWED_EXTENSIONS


@app.route('/uploads/<filename>')
def uploaded_file(filename):
    abs_path = os.path.join(app.config['UPLOAD_FOLDER'], filename)
    dehaze(abs_path, abs_path)
    return send_from_directory(app.config['UPLOAD_FOLDER'], filename)


@app.route('/', methods=['GET', 'POST'])
def upload_file():
    if request.method == 'POST':
        file = request.files['file']
        if file and allowed_file(file.filename):
            filename = secure_filename(file.filename)
            file.save(os.path.join(app.config['UPLOAD_FOLDER'], filename))
            return redirect(url_for('uploaded_file', filename=filename))
    return html


if __name__ == '__main__':
    app.run("0.0.0.0", port=8083, debug=True)
