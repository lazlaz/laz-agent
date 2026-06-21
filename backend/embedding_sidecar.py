"""Text2Vec Embedding Sidecar — HTTP wrapper for text2vec-large-chinese."""
import sys
import os
from flask import Flask, request, jsonify
from sentence_transformers import SentenceTransformer

MODEL_PATH = os.environ.get("MODEL_PATH", r"D:\models\huggingface")
MODEL_NAME = os.environ.get("MODEL_NAME", "text2vec-large-chinese")
PORT = int(os.environ.get("PORT", "9876"))

app = Flask(__name__)

print(f"Loading model {MODEL_NAME} from {MODEL_PATH}...")
model = SentenceTransformer(MODEL_NAME, cache_folder=MODEL_PATH)
print("Model loaded.")


@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok", "model": MODEL_NAME})


@app.route("/encode", methods=["POST"])
def encode():
    data = request.get_json()
    texts = data.get("texts", [])
    if not texts:
        return jsonify({"error": "texts is required"}), 400
    embeddings = model.encode(texts, normalize_embeddings=True)
    return jsonify({"embeddings": [e.tolist() for e in embeddings]})


if __name__ == "__main__":
    app.run(host="127.0.0.1", port=PORT)
