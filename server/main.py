from waitress import serve
from server import app, PORT


if __name__ == '__main__':
    print("Starting prod..")
    
    serve(app, host="0.0.0.0", port=PORT, connection_limit=100)