from fastapi import FastAPI

app = FastAPI()


@app.get("/")
def read_root():
    return {"status": "ok"}


@app.get("/items/{item}")
def read_item(item: str):
    return recommend_similar(find_movie(item))



import pandas as pd
import numpy as np

dataset = pd.read_csv('tmdb_5000_movies.csv')

import re
import nltk
nltk.download('stopwords')
from nltk.corpus import stopwords
from nltk.stem.porter import PorterStemmer
corpus = []

dataset["tags"] = (
    (dataset["keywords"] + " ") +
    (dataset["genres"] + " ") +
    dataset["overview"]
).str.strip()

for i in range(0, 4803):
    review = re.sub(r'[^a-zA-Z]', ' ', str(dataset['tags'][i]))
    review = re.sub(r'\b(id|name)\b', ' ', review)
    review = review.lower()
    review = review.split()
    ps = PorterStemmer()
    all_stopwords = stopwords.words('english')
    review = [ps.stem(word) for word in review if not word in set(all_stopwords)]
    review = ' '.join(review)
    corpus.append(review)

print(corpus)

from sklearn.feature_extraction.text import TfidfVectorizer
tfidf = TfidfVectorizer(
    max_features=4500,
    stop_words="english",
    ngram_range=(1, 2)
)

x = tfidf.fit_transform(corpus).toarray()

from sklearn.metrics.pairwise import cosine_similarity

cs = cosine_similarity(x)

dataset['title'] = dataset['title'].str.lower()

import difflib

def find_movie(user_input, cutoff=0.6):
    user_input = user_input.lower().strip()
    titles = dataset['title'].astype(str).str.lower().str.strip().tolist()

    if user_input in titles:
        return user_input

    matches = difflib.get_close_matches(user_input, titles, n=1, cutoff=cutoff)

    if not matches:
        print("Movie not found.")
        return None

    return matches[0]

def recommend_similar(movie_name, top_n=10):
    if movie_name not in dataset['title'].values:
        return {"error": "Movie not found in dataset."}

    idx = dataset[dataset['title'] == movie_name].index[0]
    sim_scores = list(enumerate(cs[idx]))
    sim_scores = sorted(sim_scores, key=lambda x: x[1], reverse=True)[1:top_n+1]

    recommendations = []
    for i, score in sim_scores:
        recommendations.append({
            "title": dataset.iloc[i]['title'],
            "score": round(float(score), 3)
        })

    return {
        "movie": movie_name,
        "top_n": top_n,
        "recommendations": recommendations
    }
    