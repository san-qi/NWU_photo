FROM unikrn/python-opencv:testing

WORKDIR /usr/src/app

COPY . .
RUN pip3 install --upgrade pip -i https://mirrors.aliyun.com/pypi/simple/
RUN pip3 install --no-cache-dir -r requirements.txt -i https://mirrors.aliyun.com/pypi/simple/
RUN pip3 install --no-cache-dir gunicorn -i https://mirrors.aliyun.com/pypi/simple/

CMD ["gunicorn", "-w", "2", "-b", ":7999", "app:app"]
